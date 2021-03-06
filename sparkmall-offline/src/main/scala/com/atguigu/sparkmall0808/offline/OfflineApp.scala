package com.atguigu.sparkmall0808.offline

import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import com.alibaba.fastjson.{JSON, JSONObject}
import com.atguigu.sparkmall0808.common.{ConfigUtil, JdbcUtil}
import com.atguigu.sparkmall0808.common.bean.UserVisitAction
import com.atguigu.sparkmall0808.offline.app.{SessionExtractorApp, SessionStatApp}
import com.atguigu.sparkmall0808.offline.bean.SessionInfo
import com.atguigu.sparkmall0808.offline.utils.SessionAccumulator
import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, sql}
import org.apache.spark.sql.{SaveMode, SparkSession}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

object OfflineApp {



  def main(args: Array[String]): Unit = {
    val sparkConf: SparkConf = new SparkConf().setAppName("offline").setMaster("local[*]")
    val sparkSession = SparkSession.builder().config(sparkConf).enableHiveSupport().getOrCreate()


    val taskId: String = UUID.randomUUID().toString

    val conditionConfig: FileBasedConfiguration = ConfigUtil("conditions.properties").config

    val conditionJsonString: String = conditionConfig.getString("condition.params.json")

    val conditionJsonObj: JSONObject = JSON.parseObject(conditionJsonString)

    println(conditionJsonObj.getString("startDate"))
    //1 根据过滤条件 取出符合的日志RDD集合  成为RDD[UserVisitAction]
    val userActionRDD: RDD[UserVisitAction] = readUserVisitActionRDD(sparkSession, conditionJsonObj)

    //    2 以sessionId为key 进行聚合   =》 RDD[sessionId,Iterable[UserVisitAction]]
    val sessionActionsRDD: RDD[(String, Iterable[UserVisitAction])] = userActionRDD.map { userAction => (userAction.session_id, userAction) }.groupByKey()
    //需求一
    SessionStatApp.statSession(sessionActionsRDD, sparkSession, taskId, conditionJsonString)
    println("需求一 完成!!")
    //需求二

    SessionExtractorApp.extractSession(sessionActionsRDD, sparkSession, taskId)
    println("需求二 完成!!")




  }


  def readUserVisitActionRDD(sparkSession: SparkSession, conditionJsonObj: JSONObject): RDD[UserVisitAction] = {
    var sql = " select v.* from user_visit_action v join user_info u on v.user_id=u.user_id where 1=1  "

    if (conditionJsonObj.getString("startDate") != null && conditionJsonObj.getString("startDate").length > 0) {
      sql += " and   date>= '" + conditionJsonObj.getString("startDate") + "'"
    }
    if (conditionJsonObj.getString("endDate") != null && conditionJsonObj.getString("endDate").length > 0) {
      sql += " and  date <='" + conditionJsonObj.getString("endDate") + "'"
    }
    if (conditionJsonObj.getString("startAge") != null && conditionJsonObj.getString("startAge").length > 0) {
      sql += " and  u.age >=" + conditionJsonObj.getString("startAge")
    }
    if (conditionJsonObj.getString("endAge") != null && conditionJsonObj.getString("endAge").length > 0) {
      sql += " and  u.age <=" + conditionJsonObj.getString("endAge")
    }
    println(sql)
    sparkSession.sql("use sparkmall0808");

    import sparkSession.implicits._
    //  sparkSession.sql(sql+ " limit 50").show
    sparkSession.sql(sql).as[UserVisitAction].rdd

  }

}
