# chat-server
Chatting server implemented by using Scala, Akka, Cats

# Getting Started

## Start CockroachDB

### 1. [Install CockroachDB](https://www.cockroachlabs.com/docs/stable/install-cockroachdb-mac.html)
Follow the link above and instructions to install CockroachDB
### 2. [Start a local CockroachDB cluster with secure mode](https://www.cockroachlabs.com/docs/stable/secure-a-cluster.html)
This is the process of running the CockroachDB and it includes configuration for authentication. Authentication is used for secure connection between CockroachDB and chat-server. The command `cockroach cert create-client` must be run in both root and chatserver. We recommend you to set the dir for authentication to be ($HOME)/.cockroach-certs, otherwise you will have to set COCKROACH_CERTS_DIR before running chat-server. 
### 3. Change cert file type to pk8
Since Jdbc does not support pem authentication file, you have to use command below to convert the authentication file to pk8
```$xslt
ls $HOME/.cockroach-certs
openssl pkcs8 -topk8 -inform PEM -outform DER -in client.root.key -out client.root.pk8 -nocrypt
openssl pkcs8 -topk8 -inform PEM -outform DER -in client.chatserver.key -out client.chatserver.pk8 -nocrypt
```
### 4. Start a single CockroachDB instance
`cockroach start` to run a single node DB
### 5. Set chat-sever schema
`cockroach sql` `scripts/init.sql` to run sql.

## Start chat-server
### 1. [Install Java8 or openjdk 11](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
### 2. [Install Scala](https://www.scala-lang.org/download/)
### 3. [Install SBT](https://www.scala-sbt.org/1.0/docs/Setup.html)
### 4. Run chat-server
While CockroachDB is running, run command below to start chat-server
```$xslt
// chat-server directory에서 실행
git submodule init
git submodule update
sbt
compile
run
```
### 5. [chat-sevice API Protocol](https://github.com/cose451-asu/chat-service)
Running chat-server provides gRPC API in chat-service

## Apply TLS to chat-server
Change the following env. Variables to run server in TLS mode 
```$xslt
an X.509 certificate chain file in PEM format: CHAT_TLS_CERT_CHAIN_FILE 
a PKCS#8 private key file in PEM format: CHAT_TLS_KEY_FILE
```
