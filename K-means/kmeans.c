#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <math.h>
#include <time.h>
#include <pthread.h>
#include <string.h>
#include "centroids.h"
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

typedef struct {
    double (*points)[DIM];
    double (*centroids)[DIM];
    int size;
} ThreadArgs;

bool update_centroid(double centroids[][DIM], double means[][DIM], unsigned int index) {
    bool ret = true;

    if(euclidian_distance(centroids[index], means[index]) > THRESHOLD)
        ret = false;

    for(int j = 0; j < DIM; j++)
        centroids[index][j] = means[index][j];

    return ret;
}

void print_point_to_file(double* point, int assigned_centroid) {
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

void assigning_points(double centroids[][DIM], double points[][DIM], double sum[][DIM], int count[], int size, bool to_print) {
    for (int i = 0; i < size; i++) {
        double min_distance = euclidian_distance(centroids[0], points[i]);
        unsigned int assigned_centroid = 0;

        for (int j = 1; j < K; j++) {
            double d = euclidian_distance(centroids[j], points[i]);
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

void print_centroids_to_file(double centroids[][DIM]) {
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

    double (*points)[DIM] = args->points;
    double (*centroids)[DIM] = args->centroids;
    int size = args->size;

    size_t size_count = K * sizeof(int);
    size_t size_sum   = K * DIM * sizeof(double);
    void* buffer = malloc(size_count + size_sum);
    if (!buffer) {
        perror("malloc");
        pthread_exit(NULL);
    }

    int* count = (int*)buffer;
    double (*sum)[DIM] = (double (*)[DIM])((char*)buffer + size_count);

    for (int i = 0; i < K; i++) count[i] = 0;
    for (int i = 0; i < K; i++)
        for (int j = 0; j < DIM; j++)
            sum[i][j] = 0.0;
            
    assigning_points(centroids, points, sum, count, size, false);

    return buffer;
}

int main(int argc, char *argv[]) {

    int n_threads = -1;
    char* points_file_path = PATH;
    char* centroids_file_path = "initial_centroids.txt";
    int mode = 1; // 0: fixed-T, 1: convergence
    int iters_or_max = MAX_ITER;
    double tol_sq = 1e-6;
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
            tol_sq = strtod(argv[++i], NULL);
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

    double centroids[K][DIM];
    for(int i = 0; i < K; i++){
        for(int j = 0; j < DIM; j++)
            centroids[i][j] = initial_centroids[i][j];
    }

    double (*points)[DIM] = malloc(sizeof(double) * N_POINTS * DIM);
    if (!points) { perror("malloc points failed"); exit(1); }

    pthread_t threads[N_THREADS];

    char bin_path[1024];
    snprintf(bin_path, sizeof(bin_path), "%s_cache_float64.bin", points_file_path);
    FILE *bin_file = fopen(bin_path, "rb");

    int actual_n_points = 0;

    if (bin_file != NULL) {
        size_t read_elements = fread(points, sizeof(double), N_POINTS * DIM, bin_file);
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
                double val = strtod(ptr, &endptr);
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
            fwrite(points, sizeof(double), actual_n_points * DIM, bin_file);
            fclose(bin_file);
            printf("Binary cache created at %s\n", bin_path);
        }
    }

    if (actual_n_points == 0) {
        fprintf(stderr, "Error: No valid points read from file.\n");
        exit(1);
    }

    int iter = 0;
    struct timespec start_time, end_time;
    clock_gettime(CLOCK_MONOTONIC, &start_time);

    while(iter < iters_or_max){ 
        int count[K] = {0};
        double sum[K][DIM] = {0};
        double mean[K][DIM] = {0};

        ThreadArgs args[N_THREADS];

        int offset = 0;
        for (int i = 0; i < N_THREADS; i++) {
            args[i].points = points + i * (actual_n_points / N_THREADS) + offset;
            args[i].centroids = centroids;
            args[i].size = (actual_n_points / N_THREADS);
            if(actual_n_points % N_THREADS != 0 && i >= N_THREADS - actual_n_points % N_THREADS){
                args[i].size++;
                offset++;
            }

            pthread_create(&threads[i], NULL, work, &args[i]);
        }

        for (int i = 0; i < N_THREADS; i++) {
            void* result = NULL;
            pthread_join(threads[i], &result);
            
            if (result == NULL) continue;

            int* i_t_count = (int*)result;
            double (*i_t_sum)[DIM] = (double (*)[DIM])((char*)result + K * sizeof(int));

            for(int j = 0; j < K; j++){
                count[j] += i_t_count[j];
                for(int s = 0; s < DIM; s++)
                    sum[j][s] += i_t_sum[j][s];
            }
            
            free(result);
        }

        double center_shift_sq = 0.0;
        for(int i = 0; i < K; i++){
            for(int j = 0; j < DIM; j++)
                if (count[i] > 0) {
                    mean[i][j] = sum[i][j] / count[i];
                } else {
                    mean[i][j] = centroids[i][j];
                }

            center_shift_sq += squared_distance(centroids[i], mean[i]);
            for(int j = 0; j < DIM; j++) centroids[i][j] = mean[i][j];
        }

        iter++;
        if(mode == 1 && center_shift_sq < tol_sq) break;
    }

    clock_gettime(CLOCK_MONOTONIC, &end_time);

    double time_taken = (end_time.tv_sec - start_time.tv_sec) * 1e3 +
                        (end_time.tv_nsec - start_time.tv_nsec) / 1e6;

    if (generate_output) {
        int count[K] = {0};
        double sum[K][DIM] = {0};
        assigning_points(centroids, points, sum, count, actual_n_points, true);
        print_centroids_to_file(centroids);
    }

    printf("CPU execution time: %.3f ms (Iterations: %d)\n", time_taken, iter);
    if (mode == 1) {
        double inertia = compute_inertia(centroids, points, actual_n_points);
        printf("Final Inertia: %f\n", inertia);
    }

    free(points);
    return 0;
}