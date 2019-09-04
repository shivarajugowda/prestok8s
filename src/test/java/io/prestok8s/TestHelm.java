package io.prestok8s;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

public class TestHelm {
    private static String namePrefix = "my-presto-cluster";

    private static class ClusterTask extends Thread {
        String name;
        boolean isCreate;
        public ClusterTask(String name, boolean isCreate){
            this.name = name;
            this.isCreate = isCreate;
        }
        public void run() {
            String[] createCmd = {"helm", "install", name,  "./presto" , "--wait",  "--set", "server.workers=0"};
            String[] deleteCmd = {"helm", "uninstall",  name};

            String[] cmd = isCreate ?  createCmd : deleteCmd;

            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command(cmd);
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }

                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorString = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    output.append(line + "\n");
                }

                int exitVal = process.waitFor();
                if (exitVal == 0) {
                    System.out.println("Success!");
                    System.out.println(output);
                } else {
                    System.out.println(output);
                    System.out.println(errorString);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateClusters(int num, boolean isCreate) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(num);
        for(int i=0; i<num; i++){
            executorService.submit(new ClusterTask(namePrefix + i, isCreate));
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        boolean createCluster = false;
        int numClusters = 100;
        long start = System.currentTimeMillis();
        updateClusters(numClusters, createCluster);
        System.out.println("\n -- Total time taken : " + (System.currentTimeMillis() - start));
    }
}
