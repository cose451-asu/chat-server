# chat-server
Chatting server implemented by using Scala, Akka, Cats

# Getting Started

## Start CockroachDB

### 1. [Install CockroachDB](https://www.cockroachlabs.com/docs/stable/install-cockroachdb-mac.html)
CockroachDB를 위 링크에서 매뉴얼대로 설치합니다.
### 2. [Start a local CockroachDB cluster with secure mode](https://www.cockroachlabs.com/docs/stable/secure-a-cluster.html)
CockroachDB를 실행하기 위한 과정이며, 인증서 세팅을 포함하고 있습니다.
해당 인증서는 CockroachDB와 chat-server 간 secure connection을 위해 사용됩니다.
`cockroach cert create-client` 명령은 root 뿐만 아니라 `chatserver` 에 대해서도 추가적으로 실행해줍니다.
인증서 경로는 {$HOME}/.cockroach-certs 로 설정하기를 권장하며, 그렇지 않은 경우 추후 COCKROACH_CERTS_DIR 환경변수를 변경하여 chat-server를 실행해야 합니다.
### 3. Change cert file type to pk8
jdbc는 pem 인증서 파일을 지원하지 않기 때문에, 아래 명령어를 통해 인증서를 pk8 형식으로 변환합니다.
```$xslt
ls $HOME/.cockroach-certs
openssl pkcs8 -topk8 -inform PEM -outform DER -in client.root.key -out client.root.pk8 -nocrypt
openssl pkcs8 -topk8 -inform PEM -outform DER -in client.chatserver.key -out client.chatserver.pk8 -nocrypt
```
### 4. Start a single CockroachDB instance
`cockroach start` 로 데이터베이스 싱글 노드를 실행합니다.
### 5. Set chat-sever schema
`cockroach sql`로 `scripts/init.sql` 내 SQL을 실행합니다.

## Start chat-server
### 1. [Install Java8 or openjdk 11](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
### 2. [Install Scala](https://www.scala-lang.org/download/)
### 3. [Install SBT](https://www.scala-sbt.org/1.0/docs/Setup.html)
### 4. Run chat-server
CockroachDB가 실행되어 있는 상태에서 다음과 같이 chat-server를 실행합니다.
```$xslt
// chat-server directory에서 실행
git submodule init
git submodule update
sbt
compile
run
```
### 5. [chat-sevice API Protocol](https://github.com/cose451-asu/chat-service)
실행된 chat-server는 위와 같은 gRPC API 들을 지원합니다.

## Apply TLS to chat-server
아래 두 개의 환경변수를 설정하면 서버가 TLS로 동작합니다. 
```$xslt
an X.509 certificate chain file in PEM format: CHAT_TLS_CERT_CHAIN_FILE 
a PKCS#8 private key file in PEM format: CHAT_TLS_KEY_FILE
```
