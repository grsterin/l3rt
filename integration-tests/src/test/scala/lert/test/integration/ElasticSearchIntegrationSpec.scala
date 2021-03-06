package lert.test.integration

import java.nio.file.{Files, Path}
import java.util.{Collections, Date, UUID}

import com.dimafeng.testcontainers.{ForEachTestContainer, GenericContainer}
import com.typesafe.scalalogging.LazyLogging
import lert.Application
import lert.core.BaseSpec
import lert.elasticsearch.ElasticSearchProcessor
import org.apache.commons.io.FileUtils
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.RestClient
import org.scalatest.BeforeAndAfterEach

import scala.collection.mutable.ListBuffer

class ElasticSearchIntegrationSpec extends BaseSpec with ForEachTestContainer with LazyLogging with BeforeAndAfterEach {
  override val container = GenericContainer(
    "elasticsearch:2.4.4",
    command = Seq("elasticsearch", "--http.cors.enabled=true", "--script.inline=on", "--script.indexed=on", "--cluster.name=lert", "--http.cors.allow-origin=\"*\""),
    exposedPorts = Seq(9200)
  )

  val files = ListBuffer[Path]()

  protected def createTempRule(rule: String): Path = {
    val path = Files.createTempFile("rule", UUID.randomUUID().toString)
    Files.write(path, rule.getBytes)
    files += path
    path
  }

  protected def addMessage(message: String)(implicit client: RestClient) = {
    val record =
      s"""{
         |   "path" : "/testlog",
         |   "@timestamp" : "${ElasticSearchProcessor.DATE_FORMAT.format(new Date())}",
         |   "@version" : "1",
         |   "host" : "MacBook-Pro",
         |   "message" : "$message",
         |   "type" : "syslog"
         |}
      """.stripMargin

    client.performRequest("PUT", s"/logstash-2017.09.05/syslog/${UUID.randomUUID().toString}", Collections.emptyMap[String, String](), new NStringEntity(record, ContentType.APPLICATION_JSON))
  }

  protected def prepareApplication(rule: Path) = {
    val home = Files.createTempDirectory(s"${this.getClass.getSimpleName}_home")
    logger.info(s"Test's home: $home")
    files += home

    System.setProperty("rules", rule.toString)
    System.setProperty("delay", "1000")
    Application.main(Array())
  }


  override protected def afterEach(): Unit = {
    super.afterEach()
    System.clearProperty("rules")
    System.clearProperty("delay")
  }

  protected def stopApplication() = {
    files.map(_.toFile).foreach(FileUtils.deleteQuietly)
    files.clear()
    Application.stopApplication()
  }
}
