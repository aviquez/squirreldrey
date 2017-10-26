package io.pablofuente.distributed.algorithm.aws.worker;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

import io.pablofuente.distributed.algorithm.aws.project.MyEvent;
import io.pablofuente.distributed.algorithm.aws.project.ProjectTask;

public class Node {
		
	ThreadPoolExecutor executor;
	Map<String, QueueListener> listeners;
	HazelcastInstance hc;

	public void start(String HAZELCAST_CONFIG) {

		int processors = Runtime.getRuntime().availableProcessors();
		System.out.println("Number of cores: " + processors);
		
		this.executor = new ThreadPoolExecutor(processors, processors,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
		
		Executors.newFixedThreadPool(processors);
		
		this.listeners = new HashMap<>();
		
		Config cfg = new Config();
		try {
			cfg = new FileSystemXmlConfig(HAZELCAST_CONFIG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		this.hc = Hazelcast.newHazelcastInstance(cfg);
		this.hc.getTopic("new-project").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			String queueId = (String) ev.getContent();
			
			System.out.println("NEW PROJECT: id [" + queueId + "]");
			
			IQueue<ProjectTask> queue = this.hc.getQueue(queueId);
			QueueListener listener = new QueueListener(queue, executor, listeners, this.hc);
			String listenerId = queue.addItemListener(listener, true);
			listener.setId(listenerId);
			listeners.put(queueId, listener);
			
			// If the worker joins the cluster when there are no more task additions to the queue, 
			// it wouldn't poll any from the queue (it only does when 'itemAdded' event fires) 
			listener.checkQueue();
		});

		/*Config config = new Config();
		ExecutorConfig executorConfig = config.getExecutorConfig("task-runner");
		executorConfig.setPoolSize(processors).setStatisticsEnabled(true);
		config.addExecutorConfig(executorConfig);

		Hazelcast.newHazelcastInstance(config);*/
	}

}
