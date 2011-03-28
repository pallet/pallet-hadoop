# Pallet & Cascalog

FIXME: write description

## Future Features

(All page numbers refer to locations in [Hadoop, the Definitive Guide, 2nd ed.](http://oreilly.com/catalog/0636920010388).)

### Network Topology Optimization

Page 248 discusses how to map out a custom network topology on hadoop using script based mapping. Essentially, we need to write a script that will take a variable number of IP addresses, and return the corresponding network locations. I'm not sure how we can do this effectively, with a pre-written script. Maybe we could use stevedore to generate a script based on all existing nodes in the cluster? Check the "Hadoop Definitive Guide" source code for an example script, here.

The other option would be to implement DNS to Switch mapping:

    public interface DNSToSwitchMapping {
      public List<String> resolve(List<String> names);
    }

* Figure out whether we want to go the script route, or the interface
  route. (Toni, what's easiest here, based on pallet's capabilities?)
* What IP addresses are we going to receive, public or private? I'm
  guessing private, but it'd be nice to have an example to reference,
  here.

### Cluster Tags

Each cluster the user creates should have a specific tag... every node that gets created should be accessed by that tag. I think that pallet can do this now, just not sure how. (`samcluster` should be different than `tonicluster`, and our commands on nodes of the same names shouldn't interfere with each other.

Some functions I'd like:

* (hadoop-property :clustertag "mapred.job.tasks")
  ;=> 10
  
### Cluster Balancing

Page 284. When we change the cluster size in some way, we need to run balancer on, I believe, the namenode.

* (Where do we need to run `balancer`?

`balancer` runs until it's finished, and doesn't get in the way of much. Here's the code for `bin/start-balancer.sh`

    bin=`dirname "$0"`
    bin=`cd "$bin"; pwd`
    
    . "$bin"/hadoop-config.sh

    # Start balancer daemon.

    "$bin"/hadoop-daemon.sh --config $HADOOP_CONF_DIR start balancer $@

### Better Ways of transferring in, out of HDFS

It would be nice to have some good interface for `distcp`, plus a few commands like `bin/hadoop fs -getmerge`. This isn't so important at all for cascalog & cascading, since various custom and supplied taps take care of everything, here.

### SSH Password Protection

Page 251. We can use ssh-agent to get rid of the need to supply a password when logging in.

## Hadoop Configuration Files

Turns out that the hostnames in the masters file are used to start up a secondary namenode. Weird!

### Different Node Classes

We should provide the ability to have a class of node, with a number of different images, all sitting under the same class.

The first place we can use this is for clusters of spot and non-spot nodes... then, spot nodes of varying prices. Beyond that, we might want some machines to have different capabilities than others, for whatever reason. (One can imagine a case where a fixed number of nodes are running, backed by EBS, hosting something like ElephantDB in addition to the hadoop namenode and jobtracker processes... the cluster can scale elastically beyond those nodes, but only by non-ebs-backed instances.

### Metrics Support!

See page 286. We might add support for [Ganglia](http://ganglia.info/), or FileContext. This would require proper modification of the `conf/hadoop-metrics.sh`.

### Hadoop Logging Setup

Page 142... thinking here about customization of `conf/log4j.sh`.

### Support for different Master Node Scenarios

Page 254. The three masters, in any given cluster, will be the namenode, the jobtracker, and the secondary namenode (optional). They can run on 1-3 machines, in any combination. I don't think we'll ever want more than one of each. And, of course, the startup order's important!
