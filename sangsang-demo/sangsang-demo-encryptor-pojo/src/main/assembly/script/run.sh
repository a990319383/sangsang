#!/bin/bash

# 指定JAVA_HOME，使用指定jdk时打开下面注释
# JAVA_HOME=''
JAVA_EXEC=''
if [ -d ${JAVA_HOME} ]; then
  JAVA_EXEC=${JAVA_HOME}/bin/java
else
  JAVA_EXEC=java
fi
PROFILE=''
ARGS=''
MAIN_CLASS=com.sangsang.demo.EncryptorPoJoApp
#脚本所在目录
cd `dirname $0`
cd ..
#部署目路
DEPLOY_DIR=`pwd`
#获取到当前目录的名称
SERVER_NAME=`basename $DEPLOY_DIR`
#配置文件目录
CONF_DIR=$DEPLOY_DIR/conf
#项目依赖库
LIB_DIR=$DEPLOY_DIR/lib
EXTLIB_DIR=$DEPLOY_DIR/extlib
#遍历整个目录的jar包
LIB_JARS=`ls $LIB_DIR |grep .jar|awk '{print "'$LIB_DIR'/"$0}'|tr "\n" ":"`
EXTLIB_JARS=`ls $EXTLIB_DIR |grep .jar|awk '{print "'$EXTLIB_DIR'/"$0}'|tr "\n" ":"`
LIB_JARS="$LIB_JARS:$EXTLIB_JARS"
#应用进程
PIDS=`ps -ef | grep ${JAVA_EXEC} | grep "$DEPLOY_DIR" | awk '{print $2}'`
#设置日志文件的输出目录
LOGS_DIR=/logs/web_app/$SERVER_NAME
if [ ! -d $LOGS_DIR ]; then
    mkdir $LOGS_DIR
fi

#日志
STDOUT_FILE=$LOGS_DIR/stdout.log
#JAVA 环境配置 10,100000表示生成10个文件，每个文件记录100000次gc
JAVA_OPTS=" -Djava.net.preferIPv4Stack=true -Dlog.home=$LOGS_DIR "
JAVA_MEM_OPTS=" -server -Xms256m -Xmx256m \
              -XX:+PrintGCDetails \
              -XX:+PrintGCDateStamps \
              -Xloggc:${LOGS_DIR}/gc/gc_%p.log \
              -XX:+UseGCLogFileRotation \
              -XX:NumberOfGCLogFiles=10 \
              -XX:GCLogFileSize=1M \
              -XX:+HeapDumpOnOutOfMemoryError \
              -XX:HeapDumpPath=${LOGS_DIR}/dump.hprof"
#退出标志
RETVAL="0"

function start() {
	if [ -n "$PIDS" ]; then
		echo "ERROR: The $SERVER_NAME already started!"
		echo "PID: $PIDS"
		exit $RETVAL
	fi


	echo -e "Starting the $SERVER_NAME ...\c"
	nohup ${JAVA_EXEC} $JAVA_OPTS $JAVA_MEM_OPTS -classpath $CONF_DIR:$LIB_JARS $MAIN_CLASS $PROFILE $ARGS > $STDOUT_FILE 2>&1 &

	COUNT=0
	while [ $COUNT -lt 1 ]; do
		echo -e ".\c"
		sleep 1
		COUNT=`ps -f | grep ${JAVA_EXEC} | grep "$DEPLOY_DIR" | awk '{print $2}' | wc -l`
		if [ $COUNT -gt 0 ]; then
			break
		fi
	done

	echo "OK!"
	PIDS=`ps -f | grep ${JAVA_EXEC} | grep "$DEPLOY_DIR" | awk '{print $2}'`
	echo "PID: $PIDS"
	echo "STDOUT: $STDOUT_FILE"

}

function stop() {
	if [ -z "$PIDS" ]; then
    	echo "ERROR: The $SERVER_NAME does not started!"
    	exit 1
	fi

	echo -e "Stopping the $SERVER_NAME ...\c"
	for PID in $PIDS ; do
    		kill -9 $PID > /dev/null 2>&1
	done

	COUNT=0
	while [ $COUNT -lt 1 ]; do
    		echo -e ".\c"
    		sleep 1
    		COUNT=1
    		for PID in $PIDS ; do
        	PID_EXIST=`ps -f -p $PID | grep ${JAVA_EXEC}`
        	if [ -n "$PID_EXIST" ]; then
            		COUNT=0
            		break
        	fi
    		done
	done

	echo "OK!"
	echo "PID: $PIDS"
	PIDS=""
}

function usage() {
	echo "Usage: $0 {start|stop|restart}"
	RETVAL="2"
}

#退出标志
RETVAL="0"

if [ ! -n "$2" ]; then
	PROFILE=''
else
	PROFILE='--spring.profiles.active='$2
fi

if [ -n "$3" ]; then
    ARGS=$3
fi

case $1 in
        start)
                start
                ;;
        stop)
                stop
                ;;
        restart)
                stop
                sleep 10
                start
                ;;
        *)
                ;;
esac
exit $RETVAL

