package com.lzx.forkjoin;


/**
 * 单线程累加
 */
public class SumNormal {
    public static void main(String[] args) {
        int count = 0;
        int[] src = MarkArray.makeArray();
        long start = System.currentTimeMillis();
        for (int i = 0; i < src.length; i++) {
            //TODO
            count = count + src[i];
        }
        System.out.println("the count is " + count + " spend time: " + (System.currentTimeMillis() - start) + "ms ");
    }
}
