package com.lzx.forkjoin;

import com.lzx.thread.CountTask;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

public class SumArray extends RecursiveTask<Integer> {

    private static final int THRESHOLD = MarkArray.ARRAY_LENGTH/10; //阀值，当拆分多大，不可在拆分，就进行计算。
    private int[] src;
    private int start;
    private int end;

    public SumArray(int[] src,int start, int end) {
        this.src = src;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Integer compute() {
        //任务的大小是否合适
        if((end - start)< THRESHOLD){
            System.out.println("from start ="+start+" to end ="+end);
            Integer count =0 ;
            for(int i=0;i<src.length;i++){
                count = count + src[i];
            }
            return count;
        }else{
            int middle = (start + end) / 2;
            SumArray leftTask = new SumArray(src,start, middle);
            SumArray rightTask = new SumArray(src,middle + 1, end);
            invokeAll(leftTask,rightTask);
            return leftTask.join()+rightTask.join();
        }

    }

    public static void main(String[] args)  {
        int[] src = MarkArray.makeArray();
        /*new 池子*/
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        SumArray countTask = new SumArray(src,0, src.length-1);
        long start = System.currentTimeMillis();
        //同步invoke, 异步submit或者execture
        forkJoinPool.invoke(countTask);
        //forkJoinPool.submit(countTask);
        System.out.println("the count is " + countTask.join() + " spend time: " + (System.currentTimeMillis() - start) + "ms ");
    }
}
