pipejine
========

Flexible, Safe and Simple Multi-Consumer/Producer Clojure Pipelines

Main features;
* Flexible number of producers and consumers, for each queue in the pipeline
* Configurable for number of consumers and producers per queue
* Supports any DAG-like topology
* Trickles "done" through the pipeline, so you can easily know then the entire computation is done
* Safe, caters with failing consumers and producers
* Very simple, exposes the queues to your application for monitoring etc
* Any part of the pipeline can be prematurely shutdown (before producers are done)
* Fast
