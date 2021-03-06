
# WARNING: AUTOGENERATED CODE
#
#    This code was generated by a tool.
#    Autogenerated on: 2020-06-01 00:45:37
#    
#    Manual changes to this file may cause unexpected behavior in your application.
#    Manual changes to this file will be overwritten if the code is regenerated.


# ##############################################################################
#' UserClient methods
#' @include AllClasses.R
#' @include AllGenerics.R
#' @include commons.R

#' @description This function implements the OpenCGA calls for managing Users.

#' The following table summarises the available *actions* for this client:
#'
#' | endpointName | Endpoint WS | parameters accepted |
#' | -- | :-- | --: |
#' | create | /{apiVersion}/users/create | body[*] |
#' | login | /{apiVersion}/users/login | body |
#' | password | /{apiVersion}/users/password | body[*] |
#' | configs | /{apiVersion}/users/{user}/configs | user[*], name |
#' | updateConfigs | /{apiVersion}/users/{user}/configs/update | user[*], action, body[*] |
#' | filters | /{apiVersion}/users/{user}/filters | user[*], id |
#' | updateFilters | /{apiVersion}/users/{user}/filters/update | user[*], action, body[*] |
#' | updateFilter | /{apiVersion}/users/{user}/filters/{filterId}/update | user[*], filterId[*], body[*] |
#' | info | /{apiVersion}/users/{user}/info | include, exclude, user[*] |
#' | projects | /{apiVersion}/users/{user}/projects | include, exclude, limit, skip, user[*] |
#' | update | /{apiVersion}/users/{user}/update | user[*], body[*] |
#'
#' @md
#' @seealso \url{http://docs.opencb.org/display/opencga/Using+OpenCGA} and the RESTful API documentation
#' \url{http://bioinfo.hpc.cam.ac.uk/opencga-prod/webservices/}
#' [*]: Required parameter
#' @export

setMethod("userClient", "OpencgaR", function(OpencgaR, user, filterId, endpointName, params=NULL, ...) {
    switch(endpointName,

        #' @section Endpoint /{apiVersion}/users/create:
        #' Create a new user.
        #' @param data JSON containing the parameters.
        create=fetchOpenCGA(object=OpencgaR, category="users", categoryId=NULL, subcategory=NULL, subcategoryId=NULL,
                action="create", params=params, httpMethod="POST", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/login:
        #' Get identified and gain access to the system.
        #' @param data JSON containing the authentication parameters.
        login=fetchOpenCGA(object=OpencgaR, category="users", categoryId=NULL, subcategory=NULL, subcategoryId=NULL,
                action="login", params=params, httpMethod="POST", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/password:
        #' Change the password of a user.
        #' @param data JSON containing the change of password parameters.
        password=fetchOpenCGA(object=OpencgaR, category="users", categoryId=NULL, subcategory=NULL, subcategoryId=NULL,
                action="password", params=params, httpMethod="POST", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/configs:
        #' Fetch a user configuration.
        #' @param user User ID.
        #' @param name Unique name (typically the name of the application).
        configs=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory=NULL, subcategoryId=NULL,
                action="configs", params=params, httpMethod="GET", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/configs/update:
        #' Add or remove a custom user configuration.
        #' @param user User ID.
        #' @param action Action to be performed: ADD or REMOVE a group. Allowed values: ['ADD', 'REMOVE']
        #' @param data JSON containing anything useful for the application such as user or default preferences. When removing, only the id will be necessary.
        updateConfigs=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory="configs",
                subcategoryId=NULL, action="update", params=params, httpMethod="POST", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/filters:
        #' Fetch user filters.
        #' @param user User ID.
        #' @param id Filter id. If provided, it will only fetch the specified filter.
        filters=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory=NULL, subcategoryId=NULL,
                action="filters", params=params, httpMethod="GET", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/filters/update:
        #' Add or remove a custom user filter.
        #' @param user User ID.
        #' @param action Action to be performed: ADD or REMOVE a group. Allowed values: ['ADD', 'REMOVE']
        #' @param data Filter parameters. When removing, only the 'name' of the filter will be necessary.
        updateFilters=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory="filters",
                subcategoryId=NULL, action="update", params=params, httpMethod="POST", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/filters/{filterId}/update:
        #' Update a custom filter.
        #' @param user User ID.
        #' @param filterId Filter id.
        #' @param data Filter parameters.
        updateFilter=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory="filters",
                subcategoryId=filterId, action="update", params=params, httpMethod="POST", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/info:
        #' Return the user information including its projects and studies.
        #' @param include Fields included in the response, whole JSON path must be provided.
        #' @param exclude Fields excluded in the response, whole JSON path must be provided.
        #' @param user User ID.
        info=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory=NULL, subcategoryId=NULL,
                action="info", params=params, httpMethod="GET", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/projects:
        #' Retrieve the projects of the user.
        #' @param include Fields included in the response, whole JSON path must be provided.
        #' @param exclude Fields excluded in the response, whole JSON path must be provided.
        #' @param limit Number of results to be returned.
        #' @param skip Number of results to skip.
        #' @param user User ID.
        projects=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory=NULL, subcategoryId=NULL,
                action="projects", params=params, httpMethod="GET", as.queryParam=NULL, ...),

        #' @section Endpoint /{apiVersion}/users/{user}/update:
        #' Update some user attributes.
        #' @param user User ID.
        #' @param data JSON containing the params to be updated.
        update=fetchOpenCGA(object=OpencgaR, category="users", categoryId=user, subcategory=NULL, subcategoryId=NULL,
                action="update", params=params, httpMethod="POST", as.queryParam=NULL, ...),
    )
})