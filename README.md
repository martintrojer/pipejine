pipejine
========

Flexible, Safe and Simple Multi-Consumer/Producer Clojure Pipelines

Main features;
* Flexible number of producers and consumers, for each queue in the pipeline
* Configurable on number of consumers and producers
* Support any DAG topologies
* Keeps track of "done" through the pipeline
* Safe, caters with failing consumers and producuers
* Very simple, exposes the queues to you application for monitoring etc
* Any part of the pipeline can be shutdown (before prodcuers are done)
* Fast
