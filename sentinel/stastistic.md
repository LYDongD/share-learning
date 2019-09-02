## sentinel metric 统计算法原理

### 滑动时间窗口相关组件

在sentinel中，负责统计的节点叫**StatisticSlot**，它对方法的入和出都进行拦截，并委托StatisticNode完成metric指标的统计和保存。**StatiscNode**通过**滑动时间窗口算法**，对监控指标进行计算和保存。


>  StatiscNode 支持的两个时间窗口 (ArrayMetric)

* rollingCounterInSecond 

1s的时间窗口，包含2个分片，相当于每个分片500ms。

```

private transient volatile Metric rollingCounterInSecond = new ArrayMetric(2,
        1000)

```

* rollingCounterInMinute 

1min的时间窗口，包含60个分片, 相当于每个分片1s。

```
private transient Metric rollingCounterInMinute = new ArrayMetric(60, 60 * 1000, false);

```

**举例来说：**

对于一个长度为1min的时间窗口，假如被分成60片，则每片的时间长度为1s。请求到来时，总是会落在滑动时间窗口内的某个分片内，并不断更新或淘汰旧的时间分片。

> 时间窗口包含3个关键的组件

* LeapArray 滑动时间窗口实现
* WindowWrap 时间窗口分片
* MetricBucket 时间窗口分片数据块，一个窗口分片包含一个bucket

LeapArray 包含时间窗口的元信息，并通过数组AtomicReferenceArray保存每个时间片内的指标数据，该数组逻辑上是一个循环数组。

```
/**
     * The total bucket count is: {@code sampleCount = intervalInMs / windowLengthInMs}.
     *
     * @param sampleCount  bucket count of the sliding window
     * @param intervalInMs the total time interval of this {@link LeapArray} in milliseconds
     */
    public LeapArray(int sampleCount, int intervalInMs) {
        AssertUtil.isTrue(sampleCount > 0, "bucket count is invalid: " + sampleCount);
        AssertUtil.isTrue(intervalInMs > 0, "total time interval of the sliding window should be positive");
        AssertUtil.isTrue(intervalInMs % sampleCount == 0, "time span needs to be evenly divided");

        this.windowLengthInMs = intervalInMs / sampleCount; // 分片时长
        this.intervalInMs = intervalInMs; // 时间窗口长度
        this.sampleCount = sampleCount; //分片数

        this.array = new AtomicReferenceArray<>(sampleCount); //时间窗口容器，保存所有分片数据
    }
```

### 核心算法

####  获取当前时间窗口分片

获取当前时间窗口分片，即根据此刻时间，拿到它所属的分片，这里分成三种情况：

* case1 : 对应的分片不存在，则创建一个新的分片
* case2 : 对应的分片已存在且未过时，则直接返回该分片
* case3 : 对应的分片已存在且已过时，则更新该分片，并清空旧分片的旧统计数据

**这里涉及3个问题：**

> 如何找到对应的分片？计算公式是什么？

由于循环数组的长度和每个分片的长度是固定的，可以通过分组求模算法拿到当前时间的索引：

```
//timeMillis 当前时间戳
private int calculateTimeIdx(long timeMillis) {
	 //分组：计算当前时间落在第几组
    long timeId = timeMillis / windowLengthInMs; 
    //求模: 由于是循环数组，通过求模计算一个周期内的分组
    return (int)(timeId % array.length());
}

```

> 如何判断分片是否过时?

每个分片都有一个起始时间，通过比较分片的起始时间判断是否过时。比如此刻的起始时间分片为100，取出时间窗口对应位置时间分片的起始时间为99，99 < 100, 说明分片已过期,需要更新该分片。


**时间分片起始时间算法：**

```
//timeMillis 当前时间戳
protected long calculateWindowStart(long timeMillis) {
    return timeMillis - timeMillis % windowLengthInMs;
}

```

### 时间分片数据结构

滑动时间窗口被切分成指定数量的分片，每个分片将统计一小段时间内的指标数据。那么，MetricBucket作为分片数据的核心实现，它又是如何高效的保存和统计各个维度的指标数据呢？

MetricBucket 通过一个计数器槽，分别统计不同维度的指标数据：

```
//metric数据槽
private final LongAdder[] counters;

private volatile long minRt; //记录最小RT
 
public MetricBucket() {
    MetricEvent[] events = MetricEvent.values();
    this.counters = new LongAdder[events.length];
    for (MetricEvent event : events) {
        counters[event.ordinal()] = new LongAdder();
    }
    initMinRt();
}

```

有几个类型的指标，就有几个槽, 槽的索引值 = 指标类型枚举的枚举值

```
public enum MetricEvent {
    PASS,
    BLOCK,
    EXCEPTION,
    SUCCESS,
    RT,
    OCCUPIED_PASS
}

```

### 更新指标数据算法

以更新请求通过数为例:

当请求通过时，先获取当前时间窗口时间片，然后基于分片更新成功通过的请求数：

**时间窗口：ArrayMetric**

```
public void addPass(int count) {
	//获取当前时间片
    WindowWrap<MetricBucket> wrap = data.currentWindow();
    //基于时间片更新指标数据
    wrap.value().addPass(count);
}

```

**时间分片数据块：MetricBucket**

```

public void addPass(int n) {
	//更新pass指标
    add(MetricEvent.PASS, n);
}

//取出对应的指标槽并自增
public MetricBucket add(MetricEvent event, long n) {
    counters[event.ordinal()].add(n);
    return this;
}

```

这里的指标槽是用LongAdder实现的，它进一步把自己切分成多个细胞单元 **cell[]**，指标槽的值是所有单元格的值之和。并发更新时，多个操作很可能散布到不同的cell, 并通过**CAS**更新cell，这样竞争不会很大，提高了并发性能。

```
/**
 * Adds the given value.
 *
 * @param x the value to add
 */
public void add(long x) {
    Cell[] as;
    long b, v;
    HashCode hc;
    Cell a;
    int n;
    if ((as = cells) != null || !casBase(b = base, b + x)) {
        boolean uncontended = true;
        int h = (hc = threadHashCode.get()).code;
        if (as == null || (n = as.length) < 1 ||
            (a = as[(n - 1) & h]) == null ||
            !(uncontended = a.cas(v = a.value, v + x))) { retryUpdate(x, hc, uncontended); }
    }
}

```

关于**LongAdder**的实现细节，过于复杂，这里不展开讨论。


### 获取指标数据算法

以获取平均RT为例：

先判断是否包含成功请求，否则RT无意义；再通过时间窗口计算总RT，根据成功数计算平均RT

**StatisticNode**

```
public double avgRt() {
    long successCount = rollingCounterInSecond.success();
    if (successCount == 0) {
        return 0;
    }

    return rollingCounterInSecond.rt() * 1.0 / successCount;
}

```

**时间窗口：ArrayMetric**

遍历并获取时间窗口内的所有时间分片，累加得到一个总的RT

```
public long rt() {
        data.currentWindow();
        long rt = 0;
        List<MetricBucket> list = data.values();
        for (MetricBucket window : list) {
            rt += window.rt();
        }
        return rt;
    }
```


### 组件关系图

![统计组件相关](http://blogimage.ponymew.com/liam/sentinel 统计组件实现.jpg)













