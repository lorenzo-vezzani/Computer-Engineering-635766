#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <math.h>
#include <time.h>
#include <pthread.h>
#include <string.h>
#include <stdatomic.h>
#include "centroids-float.h"
#include "params.h"

/*
k-means algorithm is a simple method to cluster a set of points; it works as follows:
1) randomly initialize a set of k cluster;
2) every point is assigned to the nearest centroid (using euclidian distance);
3) every centroid is updated with the average of its assigned points;
4) if every centroid update is below a certain threshold -> stop.
    otherwise go back to point 2;
*/

// points structure:
// 1.0 2.4 3.1 ... 31.0

int N_THREADS;
pthread_barrier_t start_barrier;
pthread_barrier_t end_barrier;
atomic_bool global_stop = false;

// Strutture dati allocate in modo contiguo per la Reduction (Cache-Friendly)
int (*all_counts)[K];
float (*all_sums)[K][DIM];

typedef struct {
    int thread_id;
    float (*points)[DIM];
    float (*centroids)[DIM];
    int size;
} ThreadArgs;

bool update_centroid(float centroids[][DIM], float means[][DIM], unsigned int index) {
    bool ret = true;

    if(euclidian_distance(centroids[index], means[index]) > THRESHOLD)
        ret = false;

    for(int j = 0; j < DIM; j++)
        centroids[index][j] = means[index][j];

    return ret;
}

void print_point_to_file(float* point, int assigned_centroid) {
    char filename[64];
    snprintf(filename, sizeof(filename), "results/result%d.txt", assigned_centroid);

    FILE* f = fopen(filename, "a");
    if (!f) {
        perror("Errore apertura file");
        exit(EXIT_FAILURE);
    }

    for (int d = 0; d < DIM; d++) {
        fprintf(f, "%0.12f ", point[d]);
    }
    fprintf(f, "\n");
    fclose(f);
}

void assigning_points(float centroids[][DIM], float points[][DIM], float sum[][DIM], int count[], int size, bool to_print) {
    for (int i = 0; i < size; i++) {
        float min_distance = euclidian_distance(centroids[0], points[i]);
        unsigned int assigned_centroid = 0;

        for (int j = 1; j < K; j++) {
            float d = euclidian_distance(centroids[j], points[i]);
            if (d < min_distance) {
                min_distance = d;
                assigned_centroid = j;
            }
        }

        count[assigned_centroid]++;
        for (int j = 0; j < DIM; j++)
            sum[assigned_centroid][j] += points[i][j];

        if (to_print) {
            print_point_to_file(points[i], assigned_centroid);
        }
    }
}

void print_centroids_to_file(float centroids[][DIM]) {
    FILE* f = fopen("results/centroids.txt", "w");
    if (!f) {
        perror("Error opening file");
        exit(EXIT_FAILURE);
    }
    for (int i = 0; i < K; i++) {
        for (int j = 0; j < DIM; j++) {
            fprintf(f, "%0.12f ", centroids[i][j]);
        }
        fprintf(f, "\n");
    }
    fclose(f);
}

void* work(void* args_void) {
    ThreadArgs* args = (ThreadArgs*)args_void;
    int tid = args->thread_id;

    while (1) {
        // Threads wait here for start signal from main thread
        pthread_barrier_wait(&start_barrier);

        if (atomic_load(&global_stop)) break;

        // Reset degli accumulatori locali per questa iterazione
        for (int i = 0; i < K; i++) all_counts[tid][i] = 0;
        for (int i = 0; i < K; i++)
            for (int j = 0; j < DIM; j++)
                all_sums[tid][i][j] = 0.0;

        // Esecuzione Map
        assigning_points(args->centroids, args->points, all_sums[tid], all_counts[tid], args->size, false);

        // Segnala al Main Thread di aver finito i calcoli per questa iterazione
        pthread_barrier_wait(&end_barrier);
    }
    return NULL;
}

int main(int argc, char *argv[]) {

    int n_threads = -1;
    char* points_file_path = PATH;
    char* centroids_file_path = "initial_centroids.txt";
    int mode = 1; // 0: fixed-T, 1: convergence
    int iters_or_max = MAX_ITER;
    float tol_sq = 1e-6;
    bool generate_output = false;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-t") == 0 && i + 1 < argc) {
            n_threads = atoi(argv[++i]);
        } 
        else if (strcmp(argv[i], "-i") == 0 && i + 1 < argc) {
            points_file_path = argv[++i];
        } 
        else if (strcmp(argv[i], "-c") == 0 && i + 1 < argc) {
            centroids_file_path = argv[++i];
        } 
        else if (strcmp(argv[i], "-mode") == 0 && i + 1 < argc) {
            mode = atoi(argv[++i]);
        } 
        else if (strcmp(argv[i], "-iters") == 0 && i + 1 < argc) {
            iters_or_max = atoi(argv[++i]);
        } 
        else if (strcmp(argv[i], "-tol") == 0 && i + 1 < argc) {
            tol_sq = strtof(argv[++i], NULL);
        }
        else if (strcmp(argv[i], "-o") == 0) {
            generate_output = true;
        }
    }

    if (n_threads == -1) {
        fprintf(stderr, "L'opzione -t e' obbligatoria.\n");
        return 1;
    }

    N_THREADS = n_threads;
    initialize_centroids(initial_centroids, centroids_file_path);

    float centroids[K][DIM];
    for(int i = 0; i < K; i++){
        for(int j = 0; j < DIM; j++)
            centroids[i][j] = initial_centroids[i][j];
    }

    float (*points)[DIM] = malloc(sizeof(float) * N_POINTS * DIM);
    if (!points) { perror("malloc points failed"); exit(1); }

    char bin_path[1024];
    snprintf(bin_path, sizeof(bin_path), "%s_cache_float32.bin", points_file_path);
    FILE *bin_file = fopen(bin_path, "rb");

    int actual_n_points = 0;

    if (bin_file != NULL) {
        size_t read_elements = fread(points, sizeof(float), N_POINTS * DIM, bin_file);
        actual_n_points = read_elements / DIM;
        fclose(bin_file);
    } else {
        FILE *points_file = fopen(points_file_path, "r");
        if(points_file == NULL){
            perror("No such file at specified path.\n");
            exit(1);
        }

        char line[4096];

        while (actual_n_points < N_POINTS && fgets(line, sizeof(line), points_file) != NULL) {
            char *ptr = line;
            char *endptr;
            int d = 0;

            while (d < DIM) {
                float val = strtof(ptr, &endptr);
                if (ptr == endptr) break;
                points[actual_n_points][d] = val;
                ptr = endptr;
                d++;
            }

            if (d < DIM) continue;
            actual_n_points++;
        }
        fclose(points_file);
        
        bin_file = fopen(bin_path, "wb");
        if (bin_file != NULL) {
            fwrite(points, sizeof(float), actual_n_points * DIM, bin_file);
            fclose(bin_file);
            printf("Binary cache created at %s\n", bin_path);
        }
    }

    if (actual_n_points == 0) {
        fprintf(stderr, "Error: No valid points read from file.\n");
        exit(1);
    }

    all_counts = calloc(N_THREADS, sizeof(*all_counts));
    all_sums = calloc(N_THREADS, sizeof(*all_sums));

    pthread_barrier_init(&start_barrier, NULL, N_THREADS + 1);
    pthread_barrier_init(&end_barrier, NULL, N_THREADS + 1);

    pthread_t threads[N_THREADS];
    ThreadArgs args[N_THREADS];

    int offset = 0;
    for (int i = 0; i < N_THREADS; i++) {
        args[i].thread_id = i;
        args[i].points = points + i * (actual_n_points / N_THREADS) + offset;
        args[i].centroids = centroids;
        args[i].size = (actual_n_points / N_THREADS);
        if(actual_n_points % N_THREADS != 0 && i >= N_THREADS - actual_n_points % N_THREADS){
            args[i].size++;
            offset++;
        }
        pthread_create(&threads[i], NULL, work, &args[i]);
    }

    int iter = 0;
    struct timespec start_time, end_time;
    clock_gettime(CLOCK_MONOTONIC, &start_time);

    while(iter < iters_or_max){ 
        pthread_barrier_wait(&start_barrier);

        pthread_barrier_wait(&end_barrier);

        int total_counts[K] = {0};
        float total_sums[K][DIM] = {0};

        for (int t = 0; t < N_THREADS; t++) {
            for (int k = 0; k < K; k++) {
                total_counts[k] += all_counts[t][k];
                for (int d = 0; d < DIM; d++) {
                    total_sums[k][d] += all_sums[t][k][d];
                }
            }
        }

        float center_shift_sq = 0.0;
        for (int k = 0; k < K; k++) {
            float mean[DIM];
            for (int d = 0; d < DIM; d++) {
                if (total_counts[k] > 0) {
                    mean[d] = total_sums[k][d] / total_counts[k];
                } else {
                    mean[d] = centroids[k][d];
                }
            }

            center_shift_sq += squared_distance(centroids[k], mean);
            for (int d = 0; d < DIM; d++) {
                centroids[k][d] = mean[d];
            }
        }

        iter++;
        if (mode == 1 && center_shift_sq < tol_sq) {
            global_stop = true;
            break;
        } else if (iter >= iters_or_max) {
            global_stop = true;
            break;
        }
    }

    pthread_barrier_wait(&start_barrier);

    for (int i = 0; i < N_THREADS; i++) {
        pthread_join(threads[i], NULL);
    }

    clock_gettime(CLOCK_MONOTONIC, &end_time);

    float time_taken = (end_time.tv_sec - start_time.tv_sec) * 1e3 +
                        (end_time.tv_nsec - start_time.tv_nsec) / 1e6;

    if (generate_output) {
        int count[K] = {0};
        float sum[K][DIM] = {0};
        assigning_points(centroids, points, sum, count, actual_n_points, true);
        print_centroids_to_file(centroids);
    }

    printf("CPU execution time: %.3f ms (Iterations: %d)\n", time_taken, iter);
    if (mode == 1) {
        float inertia = compute_inertia(centroids, points, actual_n_points);
        printf("Final Inertia: %f\n", inertia);
    }

    free(all_counts);
    free(all_sums);
    pthread_barrier_destroy(&start_barrier);
    pthread_barrier_destroy(&end_barrier);
    free(points);
    return 0;
}
