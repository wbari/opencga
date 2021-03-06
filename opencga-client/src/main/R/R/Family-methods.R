################################################################################
#' FamilyClient methods
#' @include commons.R
#' 
#' @description This function implements the OpenCGA calls for managing Jobs
#' @param OpencgaR an object OpencgaR generated using initOpencgaR and/or opencgaLogin 
#' where the connection and session details are stored
#' @param family a character string or a vector containing family ids
#' @param annotationsetName a character string with the annotationset name. Only 
#' necessary when updating and deleting using the familyAnnotationClient
#' @param memberId a character or a vector contaning user or group ids to 
#' work on. Mandatory when using familyAclClient
#' @param action action to be performed on the family or families
#' @param params list containing additional query or body params
#' @seealso \url{https://github.com/opencb/opencga/wiki} and the RESTful API documentation 
#' \url{http://bioinfo.hpc.cam.ac.uk/opencga/webservices/}
#' @export

setMethod("familyClient", "OpencgaR", function(OpencgaR, family, action, params=NULL, ...) {
    category <- "families"
    switch(action,
           search=fetchOpenCGA(object=OpencgaR, category=category, action=action, 
                               params=params, httpMethod="GET", ...),
           stats=fetchOpenCGA(object=OpencgaR, category=category, action=action, 
                               params=params, httpMethod="GET", ...),
           acl=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
                            action=action, params=params, httpMethod="GET", ...),
           info=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
                             action=action, params=params, httpMethod="GET", ...),
           create=fetchOpenCGA(object=OpencgaR, category=category,  
                               action=action, params=params, httpMethod="POST", ...),
           update=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
                               action=action, params=params, 
                               as.queryParam=c("incVersion", "updateIndividualVersion", "annotationSetsAction", "include", "exclude"), 
                               httpMethod="POST", ...)
           # annotationsets=fetchOpenCGA(object=OpencgaR, category=category, 
           #                             categoryId=family, action="annotationsets", 
           #                             params=params, httpMethod="GET", ...)
    )
})

#' @export
setMethod("familyAnnotationsetClient", "OpencgaR", function(OpencgaR, family, annotationSet, 
                                                         action, params=NULL, ...) {
    category <- "families"
    switch(action,
           update=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
                               subcategory="annotationSets", subcategoryId=annotationSet, 
                               action="annotations/update", params=params, 
                               as.queryParam=c("incVersion", "updateSampleVersion"), 
                               httpMethod="POST", ...)
           # search=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
           #                     subcategory="annotationsets", action="search", 
           #                     params=params, httpMethod="GET", ...),
           # delete=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
           #                     subcategory="annotationsets", subcategoryId=annotationsetName, 
           #                     action="delete", params=params, httpMethod="GET", ...),
           # create=fetchOpenCGA(object=OpencgaR, category=category, categoryId=family, 
           #                     subcategory="annotationsets", action="create", 
           #                     params=params, httpMethod="POST", as.queryParam="variableSet", ...)
           
    )
})

#' @export
setMethod("familyAclClient", "OpencgaR", function(OpencgaR, memberIds, action, params=NULL, ...) {
    category <- "families"
    switch(action,
           update=fetchOpenCGA(object=OpencgaR, category=category, subcategory="acl", 
                               subcategoryId=memberIds, action=action, params=params, 
                               httpMethod="POST", ...)
    )
})

