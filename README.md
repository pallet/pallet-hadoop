# Pallet-Hadoop #

Hadoop Cluster Management with Intelligent Defaults.

Version `0.1.0` uses Pallet 0.4.17.

# Pallet & Cascalog: Future Features

(All page numbers refer to locations in [Hadoop, the Definitive Guide, 2nd ed.](http://oreilly.com/catalog/0636920010388).)

### Network Topology Optimization

[rack awareness example...](http://www.matejunkie.com/how-to-kick-off-hadoops-rack-awareness/)

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

### Other.

NOTES:

A cluster should take in a map of arguments (ip-type, for example)
and a map of node descriptions, including base nodes for each node
type, and output a cluster object. We should have a layer of
abstraction on top of nodes, etc.

#### NOTES ON HOSTNAME RESOLUTION

It seems like this is an issue a number of folks are having. We
need to populate etc/hosts to skip DNS resolution, if we're going to
work on local machines. On EC2, I think we can get around this issue
by using the public DNS address.

Some discussion here on a way to short circuit DNS --
http://www.travishegner.com/2009/06/hadoop-020-on-ubuntu-server-904-jaunty.html

But do we want that, really?

Looks like we need to do etc/hosts internally -- we could probably
do this externally as well, with Amazon's public DNS names and
private IP addresses.

From here:
https://twiki.grid.iu.edu/bin/view/Storage/HadoopUnderstanding

For the namenode, etc to be virtualized, you must be able to access
them through DNS, or etc/hosts.

From HDFS-default --
http://hadoop.apache.org/common/docs/r0.20.2/hdfs-default.html

`dfs.datanode.dns.nameserver` -- The host name or IP address of the
name server (DNS) which a DataNode should use to determine the host
name used by the NameNode for communication and display purposes.

More support for using external hostnames on EC2
http://getsatisfaction.com/cloudera/topics/hadoop_configuring_a_slaves_hostname

How to get hadoop running without DNS --
http://db.tmtec.biz/blogs/index.php/get-hadoop-up-and-running-without-dns

Using etc/hosts as default --
http://www.linuxquestions.org/questions/linux-server-73/how-to-setup-nslookups-queries-using-etc-hosts-as-the-default-654882/

And, most clearly:

http://www.cloudera.com/blog/2008/12/securing-a-hadoop-cluster-through-a-gateway/

One “gotcha” of Hadoop is that the HDFS instance has a canonical name
associated with it, based on the DNS name of the machine — not its IP
address. If you provide an IP address for the fs.default.name, it will
reverse-DNS this back to a DNS name, then subsequent connections will
perform a forward-DNS lookup on the canonical DNS name

OTHER NOTES

* [Hadoop cluster tips and tricks](http://allthingshadoop.com/2010/04/28/map-reduce-tips-tricks-your-first-real-cluster/)
* [Discussion of rack awareness](http://hadoop.apache.org/common/docs/r0.19.2/cluster_setup.html#Configuration+Files)
* [Hadoop tutorial](http://developer.yahoo.com/hadoop/tutorial/module7.html)

#### KEY NOTES

From Noll link:
http://www.mail-archive.com/common-user@hadoop.apache.org/msg00170.html
http://search-hadoop.com/m/PcJ6xnNrSo1/Error+reading+task+output+http/v=threaded
From a note here:
http://www.michael-noll.com/tutorials/running-hadoop-on-ubuntu-linux-multi-node-cluster/#confmasters-master-only

So, we can probably do this with etc/hosts.

### More Notes

Okay, here's the good stuff. We're trying to get a system up and
running that can configure a persistent hadoop cluster.

to act as the hadoop user;

    sudo su - hadoop

With jclouds 9b, I'm getting all sorts of errors. In config, we need
to make sure we're using aws-ec2, not just ec2. Also, cake-pallet adds
pallet as a dependency, which forces jclouds beta-8... doesn't work,
if we're trying to play in 9b's world.

Either I have to go straight back to 8b, with cake-pallet and no
dependencies excluded,

## Configuring Proxy

Compile squid from scratch;

    ./configure --enable-removal-policies="heap,lru"

Then give the guys my configuration file, from my macbook.

TODO -- figure out how to get the proper user permissions, for the
squid user!

run `squid -z` the first time. `squid -N` runs with no daemon mode

[Squid Config Basics](http://www.deckle.co.za/squid-users-guide/Squid_Configuration_Basics)
[Starting Squid Guide](http://www.deckle.co.za/squid-users-guide/Starting_Squid)

## Configuring VMFest!

link over to [Toni's instructions](https://gist.github.com/867526), on
how to test this bad boy.

#### ERRORS with virtualbox

http://forums.virtualbox.org/viewtopic.php?f=6&t=24383
