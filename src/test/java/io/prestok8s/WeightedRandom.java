package io.prestok8s;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class WeightedRandom {

    static int pickRandomIdxWithInvertedWeights(double[] weights) {
        // Invert weights.
        final double twt = Arrays.stream(weights).sum();
        double[] invWeights = Arrays.stream(weights).map(x -> (twt - x)).toArray();

        double totalWeight = Arrays.stream(invWeights).sum();
        double random = Math.random() * totalWeight;
        for (int i = 0; i < invWeights.length; ++i) {
            random -= invWeights[i];
            if (random <= 0.0d)
                return i;
        }
        return -1; // Should never reach here.
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        boolean createCluster = true;
        int numClusters = 10;
        double[] weights = { 0.2, 2, 3};
        int[] results = {0,0,0};
        long start = System.currentTimeMillis();

        for(int i=0; i<10000; i++) {
            int idx = pickRandomIdxWithInvertedWeights(weights);
            results[idx] =  results[idx]+1;
        }

        Arrays.stream(results).forEach(s -> System.out.println(s));

        System.out.println("\n -- Total time taken : " + (System.currentTimeMillis() - start));
    }
}
