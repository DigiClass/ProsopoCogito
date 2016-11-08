package controllers.api

import controllers.{ BaseAuthController, HasPrettyPrintJSON }
import javax.inject.Inject
import models.annotation.AnnotationService
import models.contribution.ContributionService
import models.document.DocumentService
import models.user.UserService
import models.user.Roles._
import models.visit.VisitService
import play.api.Configuration
import play.api.libs.json.{ Json, JsObject }
import scala.concurrent.ExecutionContext

class StatsAPIController @Inject() (
    val config: Configuration,
    val annotations: AnnotationService,
    val contributions: ContributionService,
    val documents: DocumentService,
    val visits: VisitService,
    val users: UserService,
    implicit val ctx: ExecutionContext
  ) extends BaseAuthController(config, documents, users) with HasPrettyPrintJSON {

  // TODO should this be in the settings controller?
  def getContributionHistory(documentId: String, offset: Int, size: Int) = AsyncStack(AuthorityKey -> Normal) { implicit request =>
    contributions.getHistory(documentId, offset, size).map(contributions => jsonOk(Json.toJson(contributions)))
  }

  // TODO does this mix concerns too much?
  def getDashboardStats() = AsyncStack(AuthorityKey -> Admin) { implicit request =>
    val fContributionStats = contributions.getGlobalStats()
    val fTotalAnnotations = annotations.countTotal()
    val fTotalVisits = visits.countTotal()
    val fTotalUsers = users.countUsers()

    val f = for {
      stats <- fContributionStats
      annotationCount <- fTotalAnnotations
      visitCount <- fTotalVisits
      userCount <- fTotalUsers
    } yield (stats, annotationCount, visitCount, userCount)

    f.map { case (stats, annotationCount, visitCount, userCount) =>
      val response =
        Json.obj(
          "contributions" -> stats,
          "total_annotations" -> annotationCount,
          "total_visits" -> visitCount,
          "total_users" -> userCount)

      jsonOk(response)
    }
  }

}
