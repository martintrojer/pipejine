pipejine
========

Flexible, Safe, and Simple Multi-Consumer/Producer Clojure Pipelines

# Features

* Configure number of producers and consumers independently for each queue in 
  the pipeline.
* Supports any DAG-esque topology
* Native support for queue partitioning (i.e. consumers will be fed batches)
* "Done" trickles through the pipeline; easily determine when the entire computation 
  is done
* Any part of the pipeline can be prematurely shutdown before producers are done
* Safe, caters to handling failing consumers and producers
* Exposes queues to your application for simple monitoring etc.
* Fast

## Why pipejine?

For more information on what inspired pipejine and its potential uses in your 
application, see the [blog 
post](http://martintrojer.github.com/clojure/2013/03/16/flexible-multi-consumerproducer-pipelines/).
If you'd just like to see a sample usage, check out this example:
[example](https://github.com/martintrojer/pipejine/blob/master/test/pipejine/example.clj).

# Artifacts

`pipejine` is released on [Clojars](https://clojars.org/pipejine), and a history 
of releases may be found there, in addition to the tags in the repository.

## The Most Recent Release

With Leiningen, add it to your dependencies in `project.clj`:

```clojure
[pipejine "0.1.1"]
``` 


# Usage

The main namespace for pipejine is ```pipejine.core```.

## Creating a Queue

A pipeline is composed of *work queues*, which apply a *consuming function* to 
items placed into them, and *produce* the result into one or more other queues 
further downstream.

A queue is created by passing a configuration map to the `new-queue` function. 
The configuration keys and their effects are:

`:name`
:   The name of the queue; used by logging functions. 

`:queue-size`
:   The maximum size of the underlying `LinkedBlockingQueue`. Defaults to 1 if
    not set. When the queue is filled, any processes attempting to place items 
    into it with `offer` will block until a spot is available.

`:number-of-consumer-threads`
:   Number of consumers threads operating upon the queue to create. Defaults to
    1.

`:number-of-producers`
:   Number of producer queues placing items into this queue. Defaults to 1.

`:partition`
:   pipejine supports automatic batching of items before producing them into the
    next queue. 
    
    This key controls the size of those batches. This comes with the caveat that 
    only a single consumer thread can be operating in partition queues. If 
    anything other than 0 or an integer is passed the accumulator will continue 
    to gather items until all producers register they are finished sending items 
    into the queue. The default behaviour is **not** to partition.

`:time-out`
:   How long a consumer thread should wait for an item from the queue to become
    available before aborting. Time specified in milliseconds.

```clojure
;; Trivial Example
(new-queue {:name "q1"
             :queue-size 5
             :number-of-consumer-threads 5
             :number-of-producers 1})
```

## Chaining Queues, Producing & Consuming Items

### Processing Queue Items (Consumer Functions)

Once you have a queue created, you need to provide it with a consuming function 
to apply against items placed into it. 

This is done with the `spawn-consumers` function, which takes a queue and a 
function as arguments. The function should have an arity of one. For example, 
assuming you have a queue named `q1` in scope, a function that:

1. Took an item and logged it
2. Incremented the value and logged this new value
3. Produced it into another queue, `q2`

Could be written like this:

```clojure
(spawn-consumers q1 (fn [x] 
                      (log/debug (str "Value before computation " x))
                      (let [x-computation (inc x)]
                        (log/debug (str "Value after computation " x-computation))
                        (produce q2 x-computation))))
```

Any item placed into q1 will be taken off the top of the queue, have this 
function called with it as the argument, i.e. `(f item)`, and the resulting 
value placed into q2.

### Producing Items (Filling Queues)

As briefly mentioned in the previous example, you can place an item into a queue 
with the `produce` function.

It takes a queue as its first argument, and a value as the second. If the queue 
is aborted, it will return immediately. If the queue is full, it will attempt to 
offer the item for the time-out specified when the queue was created.

```clojure
(produce q1 1)
```

When no more items will be placed into a queue, call `produce-done` with the 
queue as the argument to register that it may be shutdown when all items are 
finished processing:

```clojure
(dotimes [i 20]
    (produce q1 i))
(produce-done q1)
```

### Chaining Queues (Registering Producers)

The final step in configuring a chain is registering the producers for each 
queue. 

This is done using the `producer-of` function. It takes the Queue to be 
registered as a producer of subsequent queues as the first argument. For 
example, say you had a `q1` that places items into queues `q2` and `q3` 
downstream, in a forked configuration:

```
   q1
  /  \
q2    q3
```

You would register `q1` as producer for these two like so:

```clojure
(producer-of q1 q2 q3)
```

Using the thrush macro makes it slightly easier for humans to parse this:
`(->> q1 (producer-of q2 q3))` can be read as "Queue 1 is a producer of items 
for Queue 2 and Queue 3".
