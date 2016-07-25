package models.annotation

import com.sksamuel.elastic4s.{ HitAs, RichSearchHit }
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.Indexable
import java.util.UUID
import models.geotag.ESGeoTagStore
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import storage.ES

/** Encapsulates JSON (de-)serialization so we can add it to Annotation- and AnnotationHistoryService **/
trait HasAnnotationIndexing {

  implicit object AnnotationIndexable extends Indexable[Annotation] {
    override def json(a: Annotation): String = Json.stringify(Json.toJson(a))
  }

  implicit object AnnotationHitAs extends HitAs[(Annotation, Long)] {
    override def as(hit: RichSearchHit): (Annotation, Long) =
      (Json.fromJson[Annotation](Json.parse(hit.sourceAsString)).get, hit.version)
  }

}

object AnnotationService extends HasAnnotationIndexing with AnnotationHistoryService with ESGeoTagStore {

  private val ANNOTATION = "annotation"

  private def MAX_RETRIES = 10 // Max number of import retires in case of failure

  /** Upserts an annotation, automatically dealing with updating version history and geotags.
    *
    * Returns a boolean flag indicating successful completion, the internal ElasticSearch
    * version, and the previous version of the annotation, if any.
    */
  def insertOrUpdateAnnotation(annotation: Annotation, versioned: Boolean = true)(implicit context: ExecutionContext): Future[(Boolean, Long, Option[Annotation])] = {

    // Upsert annotation in the annotation index; returns a boolean flag indicating success, plus
    // the internal ElasticSearch version number
    def upsertAnnotation(a: Annotation): Future[(Boolean, Long)] =
      ES.client execute {
        update id a.annotationId in ES.IDX_RECOGITO / ANNOTATION source a docAsUpsert
      } map { r =>
        (true, r.getVersion)
      } recover { case t: Throwable =>
        Logger.error("Error indexing annotation " + annotation.annotationId + ": " + t.getMessage)
        t.printStackTrace
        (false, -1l)
      }

    val queryPrevious = findById(annotation.annotationId)
    val queryUpsert = upsertAnnotation(annotation)

    for {
      // Retrieve previous version, if any
      maybePrevious       <- queryPrevious

      // Store new version: 1) in the annotation index, 2) in the history index
      (stored, esVersion) <- queryUpsert
      storedToHistory     <- if (stored) insertVersion(annotation) else Future.successful(false)

      // Upsert geotags for this annotation
      linksCreated        <- if (stored) insertOrUpdateGeoTagsForAnnotation(annotation) else Future.successful(false)
    } yield (linksCreated && storedToHistory, esVersion, maybePrevious.map(_._1))

  }

  /** Upserts a list of annotations, handling non-blocking chaining & retries in case of failure **/
  def insertOrUpdateAnnotations(annotations: Seq[Annotation], retries: Int = MAX_RETRIES)(implicit context: ExecutionContext): Future[Seq[Annotation]] =
    annotations.foldLeft(Future.successful(Seq.empty[Annotation])) { case (future, annotation) =>
      future.flatMap { failedAnnotations =>
        insertOrUpdateAnnotation(annotation).map { case (success, _, _) =>
          if (success) failedAnnotations else failedAnnotations :+ annotation
        }
      }
    } flatMap { failed =>
      if (failed.size > 0 && retries > 0) {
        Logger.warn(failed.size + " annotations failed to import - retrying")
        insertOrUpdateAnnotations(failed, retries - 1)
      } else {
        Logger.info("Successfully imported " + (annotations.size - failed.size) + " annotations")
        if (failed.size > 0)
          Logger.error(failed.size + " annotations failed without recovery")
        else
          Logger.info("No failed imports")
        Future.successful(failed)
      }
    }

  /** Retrieves an annotation by ID **/
  def findById(annotationId: UUID)(implicit context: ExecutionContext): Future[Option[(Annotation, Long)]] =
    ES.client execute {
      get id annotationId.toString from ES.IDX_RECOGITO / ANNOTATION
    } map { response =>
      if (response.isExists) {
        val source = Json.parse(response.getSourceAsString)
        Some((Json.fromJson[Annotation](source).get, response.getVersion))
      } else {
        None
      }
    }

  private def deleteById(annotationId: String)(implicit context: ExecutionContext): Future[Boolean] =
    ES.client execute {
      delete id annotationId.toString from ES.IDX_RECOGITO / ANNOTATION
    } map { _.isFound }

  /** Deletes the annotation with the given ID **/
  def deleteAnnotation(annotationId: UUID, deletedBy: String, deletedAt: DateTime)(implicit context: ExecutionContext): Future[Option[Annotation]] = {
    
    import models.annotation.workshop.TransactionOperation._
       
    val NOP = () => Future.successful(())
    
    findById(annotationId).flatMap(_ match {
      case Some((annotation, _)) => {
   
        val f = transaction (
          op(() => insertDeleteMarker(annotation, deletedBy, deletedAt), NOP),
          op(() => deleteById(annotationId.toString), NOP),
          op(() => deleteGeoTagsByAnnotation(annotationId), NOP)
        )

        /*
        val f = for {
          markerInserted <- insertDeleteMarker(annotation, deletedBy, deletedAt)
          deleted        <- if (markerInserted) deleteById(annotationId.toString) else Future.successful(false)
          geotagsDeleted <- if (deleted) deleteGeoTagsByAnnotation(annotationId) else Future.successful(false)
        } yield geotagsDeleted
        */
        
        f.map { success =>
          if (!success)
            throw new Exception("Error deleting annotation")

          Some(annotation)
        }
      }

      case None =>
        // Annotation not found
        Future.successful(None)
    })
  }

  def countByDocId(id: String)(implicit context: ExecutionContext): Future[Long] =
    ES.client execute {
      count from ES.IDX_RECOGITO / ANNOTATION query nestedQuery("annotates").query(termQuery("annotates.document_id" -> id))
    } map { _.getCount }

  /** Retrieves all annotations on a given document **/
  def findByDocId(id: String, offset: Int= 0, limit: Int = ES.MAX_SIZE)(implicit context: ExecutionContext): Future[Seq[(Annotation, Long)]] =
    ES.client execute {
      search in ES.IDX_RECOGITO / ANNOTATION query nestedQuery("annotates").query(termQuery("annotates.document_id" -> id)) start offset limit limit
    } map(_.as[(Annotation, Long)].toSeq)

  /** Deletes all annotations on a given document **/
  def deleteByDocId(docId: String)(implicit context: ExecutionContext): Future[Boolean] =
    findByDocId(docId).flatMap { annotationsAndVersions =>
      if (annotationsAndVersions.size > 0) {
        ES.client execute {
          bulk ( annotationsAndVersions.map { case (annotation, _) => delete id annotation.annotationId from ES.IDX_RECOGITO / ANNOTATION } )
        } map {
          !_.hasFailures
        } recover { case t: Throwable =>
          t.printStackTrace()
          false
        }
      } else {
        // Nothing to delete
        Future.successful(true)
      }
    }

  /** Retrieves all annotations on a given filepart **/
  def findByFilepartId(id: UUID, limit: Int = Int.MaxValue)(implicit context: ExecutionContext): Future[Seq[(Annotation, Long)]] =
    ES.client execute {
      search in ES.IDX_RECOGITO / ANNOTATION query nestedQuery("annotates").query(termQuery("annotates.filepart_id" -> id.toString)) limit limit
    } map(_.as[(Annotation, Long)].toSeq)

  /** Retrieves annotations on a document last updated after a given timestamp **/
  def findModifiedAfter(documentId: String, after: DateTime)(implicit context: ExecutionContext): Future[Seq[Annotation]] =
    ES.client execute {
      search in ES.IDX_RECOGITO / ANNOTATION query filteredQuery query {
        nestedQuery("annotates").query(termQuery("annotates.document_id" -> documentId))
      } postFilter {
        rangeFilter("last_modified_at").gt(formatDate(after))
      } limit Int.MaxValue
    } map { _.as[(Annotation, Long)].toSeq.map(_._1) }

  /** Rolls back the document to the state at the given timestamp **/
  def rollbackToTimestamp(documentId: String, timestamp: DateTime)(implicit context: ExecutionContext): Future[Boolean] = {

    // Rolls back one annotation, i.e. updates to the latest state recorded in the history or deletes
    def rollbackOne(annotationId: String): Future[Boolean] =
      getAnnotationStateAt(annotationId, timestamp).flatMap(_ match {
        case Some(historyRecord) =>
          if (historyRecord.deleted)
            // The annotation was already deleted at the rollback state - do nothing
            Future.successful(true)
          else
            insertOrUpdateAnnotation(historyRecord.asAnnotation, false).map(_._1)

        case None =>
          // The annotation did not exist at the rollback time - delete
          deleteById(annotationId)
      }).recover { case t: Throwable =>
        t.printStackTrace()
        Logger.warn("Rollback failed for " + annotationId)
        false
      }

    // Rolls back a list of annotations, i.e. updates to latest state recorded in the history or deletes
    def rollbackAnnotations(annotations: Seq[String]): Future[Seq[String]] =
      annotations.foldLeft(Future.successful(Seq.empty[String])) { case (future, annotationId) =>
        future.flatMap { failedAnnotationIds =>
          rollbackOne(annotationId).map { success =>
            if (success) failedAnnotationIds else failedAnnotationIds :+ annotationId
          }
        }
      }

    // Get all annotation IDs that were changed after the rollback timestamp
    val failedRollbacks = getChangedAfter(documentId, timestamp).flatMap(rollbackAnnotations(_))
    failedRollbacks.flatMap { failed =>
      if (failed.size == 0) {
        deleteHistoryRecordsAfter(documentId, timestamp)
      } else {
        Logger.warn(failed.size + " failed rollbacks")

        // TODO what would be a good recovery strategy?

        Future.successful(false)
      }
    }
  }

}
