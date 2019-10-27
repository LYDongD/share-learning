## ConcurrentHashMap源码阅读分析


源码注释参考:https://github.com/LYDongD/jdk1.8.git


### public V put(K key, V value)

#### 源码分析要点：

1 不支持null的key或value

* 抛出NPE

2 减少hash冲突的优化机制：

* 高低位混淆

3 懒加载

* 第一次插入时才初始化数组
* 类比druid

4 插入流程

* 根据key计算hash值及其索引
	* spread
* 数组为空，进行初始化
	* initTable
		* CAS 初始化数组
			* sizeCtrl
				* 1 数组初始化线程控制策略（作为标记）
				* 2 数组扩容策略（作为阈值）
* 索引位置为空，则CAS插入头节点
	* casTabAt （native方法）
* 索引位置不为空，但是正在进行扩容，则帮助扩容
	* helpTransfer
* 索引位置不为空，未进行扩容，则遍历链表或红黑树进行更新/插入
	* 遍历时对头节点加锁，锁住当前索引
* 根据链表节点数判断是否需要进行树化（数据结构升级）
	* treeifyBin
* 如果是更新操作，需要返回旧值
* 如果是插入操作，需要更新容量并判断是否需要进行扩容
	* addCount


5 头节点hash的作用

* -1， 正在扩容
* 负数，红黑树根节点
* 非负数，链表头结点


6 线程安全控制

* CAS + 自旋 的地方 (cas失败则重试，直到满足目标条件）
	* 指定索引处插入头节点 (直到头节点不为空)
	* 初始化数组（直到数组不为空）
	* 更新数组标志sizeCtrl
	* 更新key数量（操作counterCell)
		* 更新BASECOUNT
		* 更新CELLVALUE
		* 更新资源占用标志（cellBusy)
* synchronize加锁的地方（仅锁住索引处头/根结点）
	* 遍历链表或红黑树进行更新插入
	* 树化操作
	* 扩容时迁移数组直到索引上的节点







