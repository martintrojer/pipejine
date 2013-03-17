pipejine
========

Flexible, Safe and Simple Multi-Consumer/Producer Clojure Pipelines

Main features;
* Configurable number of producers and consumers for each queue in the pipeline
* Supports any DAG-like topology
* The queues supports partitioning (i.e. consumers will be fed batches)
* Trickles "done" through the pipeline, so you can easily know when the entire computation is done
* Any part of the pipeline can be prematurely shutdown (before producers are done)
* Safe, caters with failing consumers and producers
* Very simple, exposes the queues to your application for monitoring etc
* Fast

For more info see the [example](https://github.com/martintrojer/pipejine/blob/master/test/pipejine/example.clj) and read the [blog post](http://martintrojer.github.com/clojure/2013/03/16/flexible-multi-consumerproducer-pipelines/).

### Usage

Add ```[pipejine "0.1.0"]``` to your leiningen :dependecies
