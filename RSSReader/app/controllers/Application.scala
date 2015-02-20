package controllers

import akka.util.Timeout
import org.jsoup.Jsoup
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import wabisabi._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

/** *
  * RSSItem is a simple record class which represents a
  * single RSS item. It is primarily used in an ArrayBuffer
  * so that the view can iterate over a set of RSS items.
  * @param title
  * @param description
  * @param link
  */
class RSSItem(val title : String, val description : String, val link: String){}


object Query {
  val client = new Client("http://localhost:9200")
  //timeout for futures
  implicit val timeout = Timeout(5 seconds)

  /** *
    * Execute arbitrary ElasticSearch queries and return an
    * ArrayBuffers of RSSItems.
    * @param query
    * @return
    */

  def executeQuery(query: String) : ArrayBuffer[RSSItem] = {
    val search = client.search("skysports", query)
    val result = Await.result(search, timeout.duration).getResponseBody
    val json: JsValue = Json.parse(result)

    val titles = json \\ "title"
    val descriptions = json \\ "description"
    val links = json \\ "link"

    var rssItems = ArrayBuffer[RSSItem]()

    for (itemsIndex <- 0 to titles.length-1){
      //some titles and descriptions have html in them, unwanted so removed
      val title = Jsoup.parse(titles(itemsIndex).toString()).text();
      val description = Jsoup.parse(descriptions(itemsIndex).toString()).text();

      //trim extra quotes
      val item = new RSSItem(title.replace("\"", ""),
                      description.toString().replace("\"", ""),
                      links(itemsIndex).toString().replace("\"", ""))

      rssItems += item
    }

    rssItems
  }
}

object Main extends Controller {
  /** *
    * Return the 20 most recent RSS items
    * @return
    */
  def index = Action{
    Ok(views.html.index(Query.executeQuery( """{"query": { "match_all": {} }, "size": 20 }""")))
  }
}

object Search extends Controller {
  /**
   * Return all RSS items matching query
   * @param query
   * @return
   */
  def query(query: String) = Action {
    Ok(views.html.index(Query.executeQuery("""{"query": {"multi_match" : {"query" : " """ + query + """","fields": ["description","title"]}}}""")))
  }

}
