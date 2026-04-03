#!/bin/bash

#### 项目自定义变量 开始 ####
# spring boot 启动类，必须
MAIN_CLASS=com.sinoiov.wlyzhwl.OrderApplication
# java命令，如果有不同版本java可以在这里配置
JAVA_EXEC=java
#### 项目自定义变量 结束 ####

#判断用户是否为lbs
loginuser() {
  USER=`whoami`
  if [ ${USER} != "lbs" ];then
    echo "Please use user 'lbs'"
    exit 1
  fi
}
loginuser

function usage(){
  echo "Usage: $0 PROFILE [ARG1 ARG2 ...] [start|stop|restart|status]"
  exit 1
}

if [ $# -le 1 ]; then
  usage
fi

# 环境参数
PROFILE=
# Spring Boot 其它参数
ARGS=
# 脚本指令
COMMAND=${@:$#}
if [ $# -eq 2 ]; then
  PROFILE=$1
else
  ARGS=${@:2:$#-1}
fi
# echo "exec server.sh with PROFILE: \"$PROFILE\", ARGS: \"$ARGS\", COMMAND: \"$COMMAND\""

#进入脚本所在目录
cd `dirname $0`
#进入项目根目录
cd ..
#项目根目录
DEPLOY_DIR=`pwd`
#获取应用名称
APP_NAME=`basename $DEPLOY_DIR`
#配置文件目录
CONF_DIR=$DEPLOY_DIR/conf
#项目依赖库
LIB_DIR=$DEPLOY_DIR/lib
EXTLIB_DIR=$DEPLOY_DIR/extlib
#遍历整个目录的jar包
LIB_JARS=`ls $LIB_DIR |grep .jar|awk '{print "'$LIB_DIR'/"$0}'|tr "\n" ":"`
EXTLIB_JARS=`ls $EXTLIB_DIR |grep .jar|awk '{print "'$EXTLIB_DIR'/"$0}'|tr "\n" ":"`
LIB_JARS="$LIB_JARS:$EXTLIB_JARS"
#设置日志文件的输出目录
LOGS_DIR=/logs/web_app/$SERVER_NAME
if [ ! -d $LOGS_DIR ]; then
    mkdir $LOGS_DIR
fi
#启动日志
STDOUT_FILE=$LOGS_DIR/stdout.log
SB_PROFILE='--spring.profiles.active='$PROFILE
# 针对不同环境可以设置不同的java选项，暂时都一样
if [ $PROFILE == "prod" ]; then
  JAVA_OPTS=" -Djava.net.preferIPv4Stack=true -Dlog.home=$LOGS_DIR"
  JAVA_MEM_OPTS=" -server -Xms2g -Xmx2g -XX:+PrintGCDetails -Xloggc:${LOGS_DIR}/gc.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=100M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOGS_DIR}/dump.hprof "
else
  JAVA_OPTS=" -Djava.net.preferIPv4Stack=true -Dlog.home=$LOGS_DIR"
  JAVA_MEM_OPTS=" -server -XX:+PrintGCDetails -Xloggc:${LOGS_DIR}/gc.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=100M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOGS_DIR}/dump.hprof "
fi

#应用进程
PIDS=`ps -ef | grep ${JAVA_EXEC} | grep "$DEPLOY_DIR" | awk '{print $2}'`

function start() {
  if [ -n "$PIDS" ]; then
    echo "ERROR: The $APP_NAME already started! PID: $PIDS"
    exit 0
  fi

  echo -e "Starting the $APP_NAME ...\c"
  nohup ${JAVA_EXEC} $JAVA_OPTS $JAVA_MEM_OPTS -classpath $CONF_DIR:$LIB_JARS $MAIN_CLASS $SB_PROFILE $ARGS > $STDOUT_FILE 2>&1 &

  COUNT=0
  while [ $COUNT -lt 1 ]; do
    echo -e ".\c"
    sleep 1
    COUNT=`ps -f | grep ${JAVA_EXEC} | grep "$DEPLOY_DIR" | awk '{print $2}' | wc -l`
    if [ $COUNT -gt 0 ]; then
      break
    fi
  done

  PIDS=`ps -f | grep ${JAVA_EXEC} | grep "$DEPLOY_DIR" | awk '{print $2}'`
  echo -e "\n$APP_NAME start success!"
  echo "PID: $PIDS STDOUT: $STDOUT_FILE"
}
function stop() {
  if [ -z "$PIDS" ]; then
      echo "ERROR: The $APP_NAME does not started!"
      exit 0
  fi

  echo -e "Stopping the $APP_NAME ...\c"
  for PID in $PIDS ; do
    kill -9 $PID
  done

  echo -e "\n$APP_NAME stop success!"
  echo "PID: $PIDS"
  PIDS=""
}
function status() {
    if [ -n "$PIDS" ]; then
        echo "${APP_NAME} is running. PID: $PIDS"
    else
        echo "${APP_NAME} is NOT running."
    fi
}
function restart() {
  stop
  start
}

case "$COMMAND" in
  "start")
    start
    ;;
  "stop")
    stop
    ;;
  "restart")
    restart
    ;;
  "status")
    status
    ;;
  *)
    usage
    ;;
esac

