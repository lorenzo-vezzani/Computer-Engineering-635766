#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <cuda_runtime.h>
#include <string.h>
#include <float.h>
#include <math.h>
#include "centroids-float.h"
#include "params.h"

/*
 * K-Means GPU v4 — Ottimizzazioni rispetto a v3:
 *
 * 1. Kernel fuso assign+accumulate: un solo kernel fa assegnazione
 *    e accumulo, leggendo i punti dalla DRAM una sola volta.
 *    Shared memory riutilizzata: prima per i centroidi, poi per le somme.
 *    Dati del punto cachati in registri tra le due fasi.
 *
 * 2. Layout SoA (Structure of Arrays) per i punti
 *    → accessi coalesced in global memory (100% efficienza transazioni)
 *
 * 3. Caricamento cooperativo dei centroidi in shared memory
 *    → tutti i thread partecipano, niente warp divergence
 *
 * 4. Riduzione in shared memory nel kernel accumulate
 *    → atomicAdd su shared (~10-20x più veloce che su global)
 *    → solo ~3906 atomicAdd globali per locazione (vs 1M)
 *
 * 5. Kernel fuso update+convergence: aggiorna centroidi e controlla
 *    convergenza euclidea in un'unica passata, senza D2D copy.
 *
 * 6. Loop interamente su GPU: solo 2 kernel + 2 memset per iterazione
 */

/* Padding +1 per eliminare bank conflicts nella shared memory.   *
 * Con DIM=32 (= n. di bank), s_data[k*32+d] mappa TUTTI i k     *
 * sullo stesso bank per ogni d → serializzazione atomicAdd.      *
 * Con stride 33: bank = (k*33+d)%32 → k diversi = bank diversi. */
#define PADDED_DIM (DIM + 1)

/* ─────────── Kernel 1: assign_and_accumulate ─────────── */
/* Fonde assign + accumulate in un unico kernel.            *
 * Shared memory riutilizzata: centroidi → somme parziali.  *
 * I dati del punto vengono letti UNA sola volta in registri*/
template <int BLOCK_SIZE>
__global__ void __launch_bounds__(BLOCK_SIZE) assign_and_accumulate(const float * __restrict__ centroids,
                                      const float * __restrict__ points_soa,
                                      float *new_sums, int *new_counts)
{
    __shared__ float s_data[K * PADDED_DIM]; /* 50×33×4 = 6600 B */
    __shared__ int   s_counts[K];            /* 50×4    =  200 B */
                                             /* Totale  ≈ 6.6 KB */
    int tid = threadIdx.x;
    int gid = blockIdx.x * blockDim.x + tid;

    /* ── Fase 1: caricamento cooperativo centroidi ── *
     * Global layout: k*DIM+d (compatto)               *
     * Shared layout: k*PADDED_DIM+d (con padding)     */
    for (int idx = tid; idx < K * DIM; idx += blockDim.x) {
        int k = idx / DIM;
        int d = idx - k * DIM;
        s_data[k * PADDED_DIM + d] = centroids[idx];
    }

    __syncthreads();

    /* ── Fase 2: trova il centroide più vicino ── *
     * Cacha i dati del punto in registri (pt[])   *
     * per riutilizzarli nella fase di accumulo     */
    float pt[DIM];
    int best_k = 0;

    if (gid < N_POINTS) {
        /* Lettura coalesced: thread consecutivi → indirizzi consecutivi */
        for (int d = 0; d < DIM; d++)
            pt[d] = points_soa[d * N_POINTS + gid];

        float best_dist = __FLT_MAX__;
        for (int k = 0; k < K; k++) {
            float dist = 0.0f;
            for (int d = 0; d < DIM; d++) {
                float diff = pt[d] - s_data[k * PADDED_DIM + d];
                dist += diff * diff;
            }
            if (dist < best_dist) {
                best_dist = dist;
                best_k = k;
            }
        }
    }

    __syncthreads();

    /* ── Fase 3: riutilizza shared memory per accumulo ── */
    for (int idx = tid; idx < K * PADDED_DIM; idx += blockDim.x)
        s_data[idx] = 0.0f;
    for (int idx = tid; idx < K; idx += blockDim.x)
        s_counts[idx] = 0;

    __syncthreads();

    /* ── Fase 4: accumulo in shared memory ── *
     * atomicAdd su SMEM con layout PADDED     *
     * → bank conflicts eliminati              */
    if (gid < N_POINTS) {
        atomicAdd(&s_counts[best_k], 1);
        for (int d = 0; d < DIM; d++)
            atomicAdd(&s_data[best_k * PADDED_DIM + d], pt[d]);
    }

    __syncthreads();

    /* ── Fase 5: scrittura risultati parziali in global ── *
     * Converte da layout padded (shared) a compatto (global) */
    for (int idx = tid; idx < K * DIM; idx += blockDim.x) {
        int k = idx / DIM;
        int d = idx - k * DIM;
        atomicAdd(&new_sums[idx], s_data[k * PADDED_DIM + d]);
    }
    for (int idx = tid; idx < K; idx += blockDim.x)
        atomicAdd(&new_counts[idx], s_counts[idx]);
}

/* ─────── Kernel 2: update_and_check_convergence ─────── */
/* Fonde update_centroids + check_convergence.              */
__global__ void update_and_check(float *centroids,
                                 const float *new_sums,
                                 const int *new_counts,
                                 int *converged_flag,
                                 int mode,
                                 float tol_sq)
{
    int k = blockIdx.x * blockDim.x + threadIdx.x;
    __shared__ float s_shift_sq;
    if (threadIdx.x == 0) s_shift_sq = 0.0f;
    __syncthreads();

    float dist_sq = 0.0f;

    if (k < K) {
        for (int d = 0; d < DIM; d++) {
            int idx = k * DIM + d;
            float old_val = centroids[idx];
            float new_val = (new_counts[k] > 0)
                            ? new_sums[idx] / (float)new_counts[k]
                            : old_val;

            float diff = new_val - old_val;
            dist_sq += diff * diff;
            centroids[idx] = new_val;
        }
        atomicAdd(&s_shift_sq, dist_sq);
    }

    __syncthreads();

    if (threadIdx.x == 0 && mode == 1) {
        if (s_shift_sq < tol_sq) {
            atomicExch(converged_flag, 1);
        } else {
            atomicExch(converged_flag, 0);
        }
    }
}

/* ──────────────── Main ──────────────── */
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
    for (int i = 0; i < K; i++)
        for (int j = 0; j < DIM; j++)
            centroids[i][j] = initial_centroids[i][j];

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
        if (points_file == NULL) {
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

            if (d < DIM) {
                continue;
            }
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
        fprintf(stderr, "Error: No valid points read.\n");
        exit(1);
    }

    float *points_soa = (float *)malloc(sizeof(float) * N_POINTS * DIM);
    if (!points_soa) { perror("malloc points_soa failed"); exit(1); }

    for (int i = 0; i < N_POINTS; i++) {
        for (int d = 0; d < DIM; d++) {
            points_soa[d * N_POINTS + i] = points[i][d];
        }
    }

    clock_gettime(CLOCK_MONOTONIC, &start_time);

    float *d_points_soa;
    cudaMalloc(&d_points_soa, N_POINTS * DIM * sizeof(float));
    cudaMemcpy(d_points_soa, points_soa, N_POINTS * DIM * sizeof(float), cudaMemcpyHostToDevice);

    float *d_centroids;
    cudaMalloc(&d_centroids, K * DIM * sizeof(float));
    cudaMemcpy(d_centroids, centroids, K * DIM * sizeof(float), cudaMemcpyHostToDevice);

    float *d_sum;
    cudaMalloc(&d_sum, K * DIM * sizeof(float));

    int *d_count;
    cudaMalloc(&d_count, K * sizeof(int));

    int *d_converged;
    cudaMalloc(&d_converged, sizeof(int));

    int iter        = 0;
    int h_converged = 0;

    while (iter < iters_or_max) {

        cudaMemset(d_sum,   0, K * DIM * sizeof(float));
        cudaMemset(d_count, 0, K * sizeof(int));

        /* 2. Assign + Accumulate (kernel fuso, 1 sola lettura punti) */
        switch (threads_x_bl) {
            case 32:   assign_and_accumulate<32><<<n_blocks, 32>>>(d_centroids, d_points_soa, d_sum, d_count); break;
            case 64:   assign_and_accumulate<64><<<n_blocks, 64>>>(d_centroids, d_points_soa, d_sum, d_count); break;
            case 128:  assign_and_accumulate<128><<<n_blocks, 128>>>(d_centroids, d_points_soa, d_sum, d_count); break;
            case 256:  assign_and_accumulate<256><<<n_blocks, 256>>>(d_centroids, d_points_soa, d_sum, d_count); break;
            case 512:  assign_and_accumulate<512><<<n_blocks, 512>>>(d_centroids, d_points_soa, d_sum, d_count); break;
            case 1024: assign_and_accumulate<1024><<<n_blocks, 1024>>>(d_centroids, d_points_soa, d_sum, d_count); break;
            default:   fprintf(stderr, "Block size %d non supportato!\n", threads_x_bl); exit(1);
        }

        cudaError_t err = cudaGetLastError();
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Error after assign_and_accumulate: %s\n", cudaGetErrorString(err));
            exit(1);
        }

        h_converged = 0;
        cudaMemcpy(d_converged, &h_converged, sizeof(int), cudaMemcpyHostToDevice);

        update_and_check<<<1, K>>>(d_centroids, d_sum, d_count, d_converged, mode, tol_sq);

        cudaMemcpy(&h_converged, d_converged, sizeof(int), cudaMemcpyDeviceToHost);

        iter++;
        if (mode == 1 && h_converged) break;
    }

    cudaMemcpy(centroids, d_centroids, K * DIM * sizeof(float), cudaMemcpyDeviceToHost);

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
            for (int j = 0; j < DIM; j++)
                printf("%f ", centroids[i][j]);
            printf("\n");
        }
    }

    cudaFree(d_points_soa);
    cudaFree(d_centroids);
    cudaFree(d_sum);
    cudaFree(d_count);
    cudaFree(d_converged);
    free(points);
    free(points_soa);

    return 0;
}
