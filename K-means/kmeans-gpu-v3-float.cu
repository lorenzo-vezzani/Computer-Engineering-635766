#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <cuda_runtime.h>
#include <string.h>
#include <float.h>
#include "centroids-float.h"
#include "params.h"

template <int BLOCK_SIZE>
__global__ void __launch_bounds__(BLOCK_SIZE) assign(const float *__restrict__ centroids, const float *__restrict__ points, int *__restrict__ labels) {

    __shared__ float s_centroids[K * DIM];

    for (int t = threadIdx.x; t < K; t += blockDim.x) {
        for (int d = 0; d < DIM; d++)
            s_centroids[t * DIM + d] = centroids[t * DIM + d];
    }

    __syncthreads();

    int i = blockIdx.x * blockDim.x + threadIdx.x;

    if (i < N_POINTS) {
        int best_k = 0;
        float best_dist = __FLT_MAX__;

        for (int k = 0; k < K; k++) {
            float dist = 0;

            for (int d = 0; d < DIM; d++) {
                float diff = points[i * DIM + d] - s_centroids[k * DIM + d];
                dist += diff * diff;
            }

            if (dist < best_dist) {
                best_dist = dist;
                best_k = k;
            }
        }
        
        labels[i] = best_k;
    }
}

template <int BLOCK_SIZE>
__global__ void __launch_bounds__(BLOCK_SIZE) accumulate(const float *__restrict__ points, const int *__restrict__ labels, float *__restrict__ new_sums, int *__restrict__ new_counts) 
{
    int pid = blockIdx.x * blockDim.x + threadIdx.x;
    if (pid >= N_POINTS) return;

    int k = labels[pid];

    for (int d = 0; d < DIM; d++)
        atomicAdd(&new_sums[k * DIM + d], points[pid * DIM + d]);

    atomicAdd(&new_counts[k], 1);
}

__global__ void update_centroids(const float *new_sums, const int *new_counts, float *centroids)
{
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx >= K * DIM) return;

    int k = idx / DIM;

    if (new_counts[k] > 0)
        centroids[idx] = new_sums[idx] / (float)new_counts[k];
}


bool update_centroid(float centroids[][DIM], float means[][DIM], unsigned int index) {
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
    float tol_sq = 1e-6f;
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
            tol_sq = strtof(argv[++i], NULL);
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
    float (*points)[DIM] = (float (*)[DIM]) malloc(sizeof(float) * N_POINTS * DIM);
    if (!points) { perror("malloc points failed"); exit(1); }

    initialize_centroids(initial_centroids, centroids_file_path);

    float centroids[K][DIM];
    for(int i = 0; i < K; i++){
        for(int j = 0; j < DIM; j++)
            centroids[i][j] = initial_centroids[i][j];
    }

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
    
    clock_gettime(CLOCK_MONOTONIC, &start_time);
    
    float *d_points;
    cudaMalloc(&d_points, N_POINTS * DIM * sizeof(float));
    cudaMemcpy(d_points, points, N_POINTS * DIM * sizeof(float), cudaMemcpyHostToDevice);

    float *d_centroids;
    cudaMalloc(&d_centroids, K * DIM * sizeof(float));

    float *d_sum;
    cudaMalloc(&d_sum, K * DIM * sizeof(float));

    int *d_count;
    cudaMalloc(&d_count, K * sizeof(int));

    int *d_labels;
    cudaMalloc(&d_labels, N_POINTS * sizeof(int));

    int iter = 0;
    while(iter < iters_or_max) 
    {
        float mean[DIM] = {0}; 
        
        cudaMemcpy(d_centroids, centroids, K * DIM * sizeof(float), cudaMemcpyHostToDevice);

        switch(threads_x_bl) {
            case 32:   assign<32><<<n_blocks, 32>>>(d_centroids, d_points, d_labels); break;
            case 64:   assign<64><<<n_blocks, 64>>>(d_centroids, d_points, d_labels); break;
            case 128:  assign<128><<<n_blocks, 128>>>(d_centroids, d_points, d_labels); break;
            case 256:  assign<256><<<n_blocks, 256>>>(d_centroids, d_points, d_labels); break;
            case 512:  assign<512><<<n_blocks, 512>>>(d_centroids, d_points, d_labels); break;
            case 1024: assign<1024><<<n_blocks, 1024>>>(d_centroids, d_points, d_labels); break;
            default:   fprintf(stderr, "Block size non supportato!\n"); exit(1);
        }

        cudaError_t err_assign = cudaGetLastError();
        if (err_assign != cudaSuccess) {
            fprintf(stderr, "CUDA Error after assign: %s\n", cudaGetErrorString(err_assign));
            exit(1);
        }

        cudaMemset(d_sum, 0, K * DIM * sizeof(float));
        cudaMemset(d_count, 0, K * sizeof(int));

        switch(threads_x_bl) {
            case 32:   accumulate<32><<<n_blocks, 32>>>(d_points, d_labels, d_sum, d_count); break;
            case 64:   accumulate<64><<<n_blocks, 64>>>(d_points, d_labels, d_sum, d_count); break;
            case 128:  accumulate<128><<<n_blocks, 128>>>(d_points, d_labels, d_sum, d_count); break;
            case 256:  accumulate<256><<<n_blocks, 256>>>(d_points, d_labels, d_sum, d_count); break;
            case 512:  accumulate<512><<<n_blocks, 512>>>(d_points, d_labels, d_sum, d_count); break;
            case 1024: accumulate<1024><<<n_blocks, 1024>>>(d_points, d_labels, d_sum, d_count); break;
            default:   break;
        }

        cudaError_t err_accumulate = cudaGetLastError();
        if (err_accumulate != cudaSuccess) {
            fprintf(stderr, "CUDA Error after accumulate: %s\n", cudaGetErrorString(err_accumulate));
            exit(1);
        }

        int h_count[K];
        float h_sum[K * DIM];

        cudaMemcpy(h_count, d_count, K * sizeof(int), cudaMemcpyDeviceToHost);
        cudaMemcpy(h_sum, d_sum, K * DIM * sizeof(float), cudaMemcpyDeviceToHost);

        float center_shift_sq = 0.0f;
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
        float inertia = compute_inertia(centroids, points, actual_n_points);
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
    cudaFree(d_labels);
    free(points);

    return 0;
}
