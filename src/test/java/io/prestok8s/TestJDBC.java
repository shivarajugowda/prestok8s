package io.prestok8s;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestJDBC {
    //public static String JDBC_URL = "jdbc:presto://localhost:9080/tpch";
    //public static String JDBC_URL = "jdbc:presto://prestok8s:9080/tpch";
    public static String JDBC_URL = "jdbc:presto://34.83.184.42:9080/tpch";
    //public static String JDBC_URL = "jdbc:presto://34.98.117.222:80/tpch";
    private static AtomicInteger counter = new AtomicInteger(0);
    private static long start = 0;

    private static String QUERY = "select returnflag, linestatus, sum(quantity) as sum_qty, \n"
            + " sum(extendedprice) as sum_base_price, sum(extendedprice * (1 - discount)) as sum_disc_price, \n"
            + " sum(extendedprice * (1 - discount) * (1 + tax)) as sum_charge, avg(quantity) as avg_qty, \n"
            + " avg(extendedprice) as avg_price, avg(discount) as avg_disc, count(*) as count_order \n"
            + " from sf1.lineitem \n"
            + " where shipdate <= date '1998-12-01' - INTERVAL '90' DAY \n"
            + " group by returnflag, linestatus "
            + " order by returnflag, linestatus " ;


    //private static String QUERY = "SELECT * FROM tiny.nation";
    private static int  EXPECTED_NUMROWS = 4;

    public static Connection getConnection() throws SQLException {
        Connection conn = null;
        Properties props = new Properties();
        props.setProperty("user", "test");
        return DriverManager.getConnection(JDBC_URL, props);
    }

    public static int traverseResultSet(ResultSet rs, boolean doPrint) throws SQLException {
        int nrows = 0;
        int columnsNumber = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            for (int i = 1; i <= columnsNumber; i++) {
                String columnValue = rs.getString(i);
                if(doPrint)
                    System.out.print(columnValue +  ", ");
            }
            nrows++;
            if(doPrint)
                System.out.println("");
        }
        return nrows;
    }

    public static class MyRunnable implements Runnable {
        public void run () {
            int nrows = 0;
            try {
                Connection conn = getConnection();

                for (int i = 0; i < 1; i++) {
                    //ResultSet rs = conn.getMetaData().getTables("tpch", "sf1", null, null);
                    PreparedStatement pstmt = conn.prepareStatement(QUERY);
                    ResultSet rs = pstmt.executeQuery();
                    nrows = traverseResultSet(rs, false);
                    rs.close();
                    pstmt.close();
                    counter.incrementAndGet();
                }

                conn.close();
            } catch (SQLException e) {
                // e.printStackTrace();
            }
            if (nrows != EXPECTED_NUMROWS) {
                System.out.println("ERROR: Number of rows in the Result Set is not equal to "
                        + EXPECTED_NUMROWS + " :  " + nrows);
                // System.exit(0);
            }

            //System.out.println("Number of rows : " + nrows);
            int totRuns = counter.get();
            if(totRuns % 5 == 0) {
                double rate = new Double(System.currentTimeMillis() - start)/totRuns;
                System.out.println("Total runs : " +  totRuns + " : Time per query (ms) : "+ rate);
            }
        }
    }

    public static void main(String[] args) {
        start = System.currentTimeMillis();

        int NTHREADS = 10;
        int NTIMES = 1000;

        ExecutorService exSvc = Executors.newFixedThreadPool(NTHREADS);

        for(int i=0; i<NTIMES; i++) {
            exSvc.submit(new MyRunnable());
        }

        exSvc.shutdown();
        try {
            exSvc.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\nTotal runs : " +  counter.get());
        System.out.println("\nTotal time taken (ms) : " +  (System.currentTimeMillis() - start));
    }
}
