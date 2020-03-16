package com.lzx.forkjoin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class FindDirsFiles extends RecursiveAction {

    private File path;

    private FindDirsFiles(File path) {
        this.path = path;
    }

    @Override
    protected void compute() {
        List<FindDirsFiles> subTasks = new ArrayList<>();

        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    //对每个子目录都新建一个子任务
                    subTasks.add(new FindDirsFiles(file));
                } else {
                    //遇到文件检查
                    if (file.getAbsolutePath().endsWith("xls")) {
                        System.out.println("文件：" + file.getAbsolutePath());
                    }
                }
            }
            if (!subTasks.isEmpty()) {
                //在当前的forkjoinpool上调度所有任务
                for (FindDirsFiles suntask : invokeAll(subTasks)) {
                    suntask.join();
                }
            }
        }
    }

    public static void main(String[] args) {
        //用一个 forkjoinpool 实例来调度总任务
        try {
            ForkJoinPool pool = new ForkJoinPool();
            FindDirsFiles task = new FindDirsFiles(new File("E:/"));
            //异步提交
            pool.execute(task);

            System.out.println("Task is Runing....");
            Thread.sleep(1);
            int otherWork = 0;
            for (int i = 0; i < 100; i++) {
                otherWork = otherWork + 1;
            }
            System.out.println("Main Thread done sth。。。。。，otherWork = " + otherWork);
            task.join();//阻塞方法
            System.out.println("Task end");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }
}
