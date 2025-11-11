# Java Web Server Performance Benchmark

## Overview

In this project I am trying to see how webservers based on different architectures perform under same load. 

Same load here = 2000 concurrent connections over a 60-second duration made from 4 different client threads

### Architectures Implemented

1.  **Single Thread (Blocking I/O):** Single threaded server.. can handle only one at a time.
2.  **Thread Pool (Blocking I/O):** A fixed pool of platform threads are serving clients. Assigns a **Thread-per-Connection** so once no of connections > pool size, it starts queueing. if all worker threads (= poolsize) are busy in IO then incoming requests wait in the task queue.. JVM will not schedule them unless some worker threads become free.. tight coupling b/w worker thread and OS thread.  
3.  **Virtual Thread Pool (Blocking I/O with Virtual Threads):** Uses Java's modern Virtual Threads (via `Executors.newVirtualThreadPerTaskExecutor()`). This still uses the **Thread-per-Connection** model but with extremely cheap, non-OS-blocking virtual threads... all tasks will be assigned a virtual thread (it uses OS thread internally anyway) but, anytime, JVM detects a blocking call, it will park that virtual thread and put another waiting virtual thread on the carrier OS thread.. thats how the Virtual model is converting our `blocking-style-code` into Non-blocking execution !.. great stuff.. only overhead is JVM needs to do all this because of our blocking-style-code..
4.  **NIO Selector (Non-Blocking I/O):** Uses the classic Java NIO API (`Selector`, `SocketChannel`) to handle thousands of connections with a very small number of OS threads.. Here we never write code that will be like a synchronous blocking call.. everything we write is in `reactive` = `non-blocking-async (promises)` style.. so OS never have to park the thread which is executing our reactive code.. problem: debugging is difficult..

---

##  Step-by-Step Implementation Details

### 1. `HttpResponse.java`

* **Purpose:** Defines the standard HTTP/1.1 response bytes array used by all servers.
* **Key Detail:** Ensures a consistent response payload across all tests.

### 2. Blocking I/O Servers (`SingleThreadServer.java`, `ThreadPoolServer.java`, `VirtualThreadPoolServer.java`)

* These all use the traditional `java.net.ServerSocket` and `java.net.Socket`.
* The critical line demonstrating blocking I/O is:
    ```java
    // Blocks the thread until the I/O operation (sleep) is complete
    Thread.sleep(200); 
    ```
* **Concurrency Model:**
    * **Single Thread:** `server.accept()` and subsequent I/O block the *only* thread.
    * **Thread Pool:** `server.accept()` runs on the main thread; client handling is submitted to a fixed `ExecutorService` (pool of platform threads).
    * **Virtual Thread Pool:** Client handling is submitted to a `newVirtualThreadPerTaskExecutor()`, where the `Thread.sleep(200)` operation **unmounts** the virtual thread from its underlying platform carrier thread, allowing the carrier thread to pick up another task, thus achieving massive concurrency.

### 3. Non-Blocking I/O Server (`NIOServer.java`)

* **Core Components:** Uses `java.nio.channels.Selector` and `java.nio.channels.SocketChannel`.
* **Event Loop:** The `selector.select()` call waits for I/O events (`OP_ACCEPT`, `OP_READ`, `OP_WRITE`).
* **Handling Delay:** Instead of a blocking `Thread.sleep(200)`, the server uses a `ScheduledExecutorService` to defer the write operation.
    * On `OP_READ`, interest is temporarily disabled (`key.interestOps(0)`).
    * After 200ms, the scheduler re-enables `OP_WRITE` interest (`key.interestOps(SelectionKey.OP_WRITE)`), which wakes up the main loop to process the response.
* **Key Insight:** A single thread can manage thousands of concurrent connections because it never blocks waiting for I/O or the simulated delay; it simply registers interest and moves on.

---

## Performance Results

The benchmark was executed with the following parameters: **2000 concurrent connections** over a **60-second duration**, with a simulated **200ms I/O latency** for every request.

### Raw Data (results/comparison.csv)

| name | connections | duration | requests\_per\_sec | lat\_avg | lat\_p50 | lat\_p99 ms |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Single Thread** | 2000 | 60 | 4.86 | 1.20s | 1.20s | 1810 |
| **Thread Pool (10 threads)** | 2000 | 60 | 49.06 | 1.04s | 1.03s | 1850 |
| **Thread Pool (50 threads)** | 2000 | 60 | 245.5 | 1.03s | 1.03s | 1850 |
| **Virtual Thread Pool (per-task)** | 2000 | 60 | **9455.56** | **206.80ms** | **204.19ms** | **315.64** |
| **NIO Selector** | 2000 | 60 | **9592.38** | **204.36ms** | **202.97ms** | **228.96** |

### Visual Comparison (comparison.png)
![comparison](results/comparison.png)


---

##  Results Analysis

### Understanding the Trade-offs

The results clearly validate the fundamental architectural constraints of I/O-bound applications:

1.  **Single Thread & Thread Pool (Blocking I/O):**
    * **Low Throughput:** The server could only handle a small fraction of the available concurrency. The max requests per second for the 50-thread pool was $\approx 245$ requests.
    * **High Latency:** Since the server must wait for a thread to become available *and* the thread is fully blocked for the 200ms delay, the average latency is approximately **5 times** the simulated delay ($\approx 1.0$s). The server's capacity limits become the bottleneck, forcing clients to queue. The 99th percentile latency is severely impacted.
    * *The Thread-per-Connection model is non-scalable when threads are blocked.*

2.  **Virtual Thread Pool & NIO Selector (High Concurrency Models):**
    * **Massive Throughput:** Both models handled a throughput increase of over **38x** compared to the 50-thread pool. Both achieved $\approx 9500$ requests/sec.
    * **Low, Stable Latency:** The average latency for both is $\approx 200$ms, which is the **minimal possible latency** because it is the simulated I/O delay. The server's I/O processing is no longer the bottleneck.
    * **Virtual Threads:** This is the easiest-to-write implementation. By adopting the Virtual Thread Pool, we keep the familiar *blocking code style* (`Thread.sleep(200)`) but achieve NIO-level performance. This is a game-changer for I/O-bound enterprise applications, as it provides high scalability without the complexity of an asynchronous, callback-based framework.
    * **NIO Selector:** While achieving the same peak throughput as Virtual Threads, the code is significantly more complex, requiring explicit I/O channel management, state attachment (`key.attach(resp)`), and an external scheduler for the simulated delay. It remains a high-performance, low-level option, but the introduction of Virtual Threads offers a compelling, higher-level alternative for most applications.

**Conclusion:** For applications dominated by I/O-bound tasks (like this simulation), the **Virtual Thread Pool** offers the superior solution, matching the performance of a complex NIO implementation while retaining the simplicity and readability of a traditional blocking thread-per-request architecture.