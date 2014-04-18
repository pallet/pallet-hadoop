# Pallet-Hadoop #

Hadoop Cluster Management with Intelligent Defaults.

## Background ##

Hadoop is an Apache java framework that allows for distributed
processing of enormous datasets across large clusters. It combines a
computation engine based on
[MapReduce](http://en.wikipedia.org/wiki/MapReduce) with
[HDFS](http://hadoop.apache.org/hdfs/docs/current/hdfs_design.html), a
distributed filesystem based on the [Google File
System](http://en.wikipedia.org/wiki/Google_File_System).

Abstraction layers such as
[Cascading](https://github.com/cwensel/cascading) (for Java) and
[Cascalog](https://github.com/nathanmarz/cascalog) (for
[Clojure](http://clojure.org/)) make writing MapReduce queries quite
nice. Indeed, running hadoop locally with cascalog [couldn't be
easier](http://nathanmarz.com/blog/introducing-cascalog-a-clojure-based-query-language-for-hado.html).

Unfortunately, graduating one's MapReduce jobs to the cluster level
isn't so easy. Amazon's [Elastic
MapReduce](http://aws.amazon.com/elasticmapreduce/) is a great option
for getting up and running fast; but what to do if you want to
configure your own cluster?

After surveying existing tools, I decided to write my own layer over
[Pallet](https://github.com/pallet/pallet), a wonderful cloud
provisioning library written in Clojure. Pallet runs on top of
[jclouds](https://github.com/jclouds/jclouds), which allows pallet to
define its operations independent of any one cloud provider. Switching
between clouds involves a change of login credentials, nothing more.

## Getting Started ##

To include pallet-hadoop in your project, add the following lines to
`:dev-dependencies` in your `project.clj` file:

```clojure
[com.palletops/pallet-hadoop "0.3.5"]
[com.palletops/pallet-jclouds "1.7.1"]
[org.apache.jclouds/jclouds-all "1.7.1"]
[org.apache.jclouds.driver/jclouds-sshj "1.7.1"]
[org.apache.jclouds.driver/jclouds-slf4j "1.7.1"]
[ch.qos.logback/logback-classic "1.0.1"]
[ch.qos.logback/logback-core "1.0.1"]
```

You'll also need to add the Sonatype repository, to get access to
Pallet. Add this k-v pair to your `project.clj` file:

```clojure
:repositories {"sonatype" "http://oss.sonatype.org/content/repositories/releases/"}
```
For a detailed example of how to run Pallet-Hadoop, see the [example
project](https://github.com/pallet/pallet-hadoop-example) here. For
more detailed information on the project's design, see [the project
wiki](https://github.com/pallet/pallet-hadoop).

Pallet-Hadoop version `0.3.4-SNAPSHOT` uses Pallet `0.7.5`, jclouds 
`1.7.1` and Clojure `1.4` and later.
