/**
 * Created by jonathan on 20/02/15.
 */

import akka.actor.{Props, Actor}
import java.util.Date
import akka.util.Timeout
import play.api._
import play.api.libs.json.{Json, JsValue}
import play.libs.Akka
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import play.libs.Akka.system
import play.api.libs.concurrent.Execution.Implicits._
import wabisabi._


object Global extends GlobalSettings {
  val client = new Client("http://localhost:9200")
  val xmlSource = xml.XML.load("http://www1.skysports.com/feeds/11095/news.xml")

  /** *
    * FetchRSS pulls from the SkySports RSS feed and pushes it to a
    * locally running ElasticSearch instance under the index "skysports"
    * and type "items". Additionally it pushes a timestamp under "skysports"/
    * "lastFetched" which is used to determine when the data store is out of date.
    * @return
    */
  def fetchRSS() = {
    Logger.info("fetching RSS")

    val lastUpdate = (xmlSource \\ "lastBuildDate").text
    val items = xmlSource \\ "item"

    //build a map for each rss item
    for (item <- items) {
      var rssMap = Map[String, String]()
      rssMap += ("title" -> (item \ "title").text)
      rssMap += ("description" -> (item \ "description").text)
      rssMap += ("date" -> (item \ "pubDate").text)
      rssMap += ("link" -> (item \ "link").text)

      //convert map to json
      val rssMapJson = scala.util.parsing.json.JSONObject(rssMap).toString()

      //push json to elasticsearch
      client.index(
        index = "skysports", `type` = "item",
        data = rssMapJson
      )
    }

    //update the last fetched time
    var lastFetchedMap = Map[String, String]()
    lastFetchedMap += ("fetchTime" -> lastUpdate.toString)
    Logger.info(lastUpdate.toString)
    val lastFetchedMapJson = scala.util.parsing.json.JSONObject(lastFetchedMap).toString()

    client.index(
      index = "skysports", `type` = "lastFetched", id=Some("1"),
      data = lastFetchedMapJson
    )


  }

  /**
   * This function executes on startup and schedules the exeuction of the
   * enclosed Akka actor at 10 minute intervals an determines when to fetch the rss feed.
   * @param app
   */
  override def onStart(app: Application): Unit = {

    val fetchRSSActor = system.actorOf(Props(new Actor {
      def receive = {
        case _ ⇒
          val checkExists = client.verifyIndex("skysports")
          val indexExists = Await.result(checkExists, (Timeout(5 seconds)).duration)

          //check if skysports index doesn't exist in elasticsearch, create it
          //and fetch rss. If it does, see if rss entries are out of date
          if (indexExists.getStatusCode != 200) {
            client.createIndex(name = "skysports")
            Logger.info("creating skysports index")
            fetchRSS()
          } else {
            //find out when the rss feed was last fetched
            val lastFetchedQuery = client.get("skysports", "lastFetched", "1")
            val lastFetchedQueryResult = Await.result(lastFetchedQuery, (Timeout(5 seconds)).duration)
            val lastFetchDateJson: JsValue = Json.parse(lastFetchedQueryResult.getResponseBody)
            val lastFetchDateString = (lastFetchDateJson \ "_source" \ "fetchTime").toString().replace("\"", "")
            val df = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
            val lastFetchDate = df.parse(lastFetchDateString)

            //find out how recent the live RSS feed is
            val lastPublishedDate: Date = df.parse((xmlSource \ "channel" \ "lastBuildDate").text)

            //if the live rss is more recent than our datastore; fetch
            if (lastPublishedDate.after(lastFetchDate)) {
              fetchRSS()
            } else {
              Logger.info("old rss. not doing anything")
            }

          }
      }

    }))

    //fetch the RSS feed every 10 minutes, this is a reasonably arbitrary interval
    Akka.system.scheduler.schedule(0 seconds, 1 minutes, fetchRSSActor, "tick")

  }
}

