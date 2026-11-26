#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <cuda_runtime.h>
#include <string.h>
#include "centroids.h"
#include "params.h"

template <int BLOCK_SIZE>
__global__ void __launch_bounds__(BLOCK_SIZE) assign(const double *__restrict__ centroids, const double *__restrict__ points, double *__restrict__ sum, int *__restrict__ count) {

    __syncthreads();

    int i = blockIdx.x * blockDim.x + threadIdx.x;

    if (i < N_POINTS) {
        int best_k = 0;
        double best_dist = __DBL_MAX__;

        for (int k = 0; k < K; k++) {
            double dist = 0;

            for (int d = 0; d < DIM; d++) {
                double diff = points[i * DIM + d] - centroids[k * DIM + d];
                dist += diff * diff;
            }

            if (dist < best_dist) {
                best_dist = dist;
                best_k = k;
            }
        }
        atomicAdd(&count[best_k], 1);

        for (int j = 0; j < DIM; j++) {
            atomicAdd(&sum[best_k * DIM + j], points[i * DIM + j]);
        }
    }
}


bool update_centroid(double centroids[][DIM], double means[][DIM], unsigned int index) {
    bool ret = true;

    if(euclidian_distance(centroids[index], means[index]) > THRESHOLD)
        ret = false;

    for(int j = 0; j < DIM; j++)
        centroids[index][j] = means[index][j];

    return ret;
}

int main(int argc, char *argv[])
{
    int threads_x_bl = THREADS_X_BL_DEF;
    const char *points_file_path = PATH;
    const char *centroids_file_path = "initial_centroids.txt";
    int mode = 1; // 0: fixed-T, 1: convergence
    int iters_or_max = MAX_ITER;
    double tol_sq = 1e-6;
    bool print_centroids = false;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-b") == 0 && i + 1 < argc) {
            threads_x_bl = atoi(argv[++i]);
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
        else if (strcmp(argv[i], "-p") == 0 || strcmp(argv[i], "--print") == 0) {
            print_centroids = true;
        }
    }

    if (threads_x_bl <= 0) {
        fprintf(stderr, "Errore: numero di thread per blocco non valido\n");
        return 1;
    }

    int n_blocks = (N_POINTS + threads_x_bl - 1) / threads_x_bl;

    struct timespec start_time, end_time;
    double (*points)[DIM] = (double (*)[DIM]) malloc(sizeof(double) * N_POINTS * DIM);
    if (!points) { perror("malloc points failed"); exit(1); }

    initialize_centroids(initial_centroids, centroids_file_path);

    double centroids[K][DIM];
    for(int i = 0; i < K; i++){
        for(int j = 0; j < DIM; j++)
            centroids[i][j] = initial_centroids[i][j];
    }

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
    
    clock_gettime(CLOCK_MONOTONIC, &start_time);

    double *d_points;
    cudaMalloc(&d_points, N_POINTS * DIM * sizeof(double));
    cudaMemcpy(d_points, points, N_POINTS * DIM * sizeof(double), cudaMemcpyHostToDevice);

    double *d_centroids;
    cudaMalloc(&d_centroids, K * DIM * sizeof(double));

    double *d_sum;
    cudaMalloc(&d_sum, K * DIM * sizeof(double));

    int *d_count;
    cudaMalloc(&d_count, K * sizeof(int));

    int iter = 0;
    while(iter < iters_or_max) { 
        double mean[DIM] = {0}; 

        cudaMemcpy(d_centroids, centroids, K * DIM * sizeof(double), cudaMemcpyHostToDevice);
        cudaMemset(d_sum, 0, K * DIM * sizeof(double));
        cudaMemset(d_count, 0, K * sizeof(int));

        switch(threads_x_bl) {
            case 32:   assign<32><<<n_blocks, 32>>>(d_centroids, d_points, d_sum, d_count); break;
            case 64:   assign<64><<<n_blocks, 64>>>(d_centroids, d_points, d_sum, d_count); break;
            case 128:  assign<128><<<n_blocks, 128>>>(d_centroids, d_points, d_sum, d_count); break;
            case 256:  assign<256><<<n_blocks, 256>>>(d_centroids, d_points, d_sum, d_count); break;
            case 512:  assign<512><<<n_blocks, 512>>>(d_centroids, d_points, d_sum, d_count); break;
            case 1024: assign<1024><<<n_blocks, 1024>>>(d_centroids, d_points, d_sum, d_count); break;
            default:   fprintf(stderr, "Block size non supportato!\n"); exit(1);
        }

        cudaError_t err = cudaGetLastError();
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Error after assign: %s\n", cudaGetErrorString(err));
            exit(1);
        }

        int h_count[K];
        double h_sum[K * DIM];

        cudaMemcpy(h_count, d_count, K * sizeof(int), cudaMemcpyDeviceToHost);
        cudaMemcpy(h_sum, d_sum, K * DIM * sizeof(double), cudaMemcpyDeviceToHost);

        double center_shift_sq = 0.0;
        for(int i = 0; i < K; i++) {
            for(int j = 0; j < DIM; j++) {
                if (h_count[i] > 0) {
                    mean[j] = h_sum[i * DIM + j] / h_count[i];
                } 
                else {
                    mean[j] = centroids[i][j];
                }
            }
            center_shift_sq += squared_distance(centroids[i], mean);
            for(int j = 0; j < DIM; j++) centroids[i][j] = mean[j];
        }

        iter++;
        if(mode == 1 && center_shift_sq < tol_sq) break;
    }

    clock_gettime(CLOCK_MONOTONIC, &end_time);
    double time_taken = (end_time.tv_sec - start_time.tv_sec) * 1e3 +
                        (end_time.tv_nsec - start_time.tv_nsec) / 1e6;

    printf("GPU execution time: %.3f ms (Iterations: %d)\n", time_taken, iter);
    if (mode == 1) {
        double inertia = compute_inertia(centroids, points, actual_n_points);
        printf("Final Inertia: %f\n", inertia);
    }

    if (print_centroids) {
        printf("\nCentroids:\n");
        for (int i = 0; i < K; i++) {
            printf("%d: ", i);
            for (int j = 0; j < DIM; j++) {
                printf("%f ", centroids[i][j]);
            }
            printf("\n");
        }
    }

    cudaFree(d_points);
    cudaFree(d_centroids);
    cudaFree(d_sum);
    cudaFree(d_count);
    free(points);

    return 0;
}
