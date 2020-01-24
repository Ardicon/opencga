// #1346

// First we update all the samples so they have a default value
db.getCollection("sample").update({}, {"$set": {"_individualUid": -1, "individualId": ""}}, {"multi": true});

var individuals = db.getCollection("individual").find({"samples" : { "$exists": true, "$ne": [] }}, {"samples": 1, "uid": 1, "id": 1});
for (var i = 0; i < individuals.length(); i++) {
    var individual = individuals[i];
    if (isNotEmptyArray(individual.samples)) {
        for (sample of individual.samples) {
            db.getCollection("sample").update({"uid": sample.uid, "version": sample.version},
                {"$set": {"_individualUid": individual.uid, "individualId": individual.id}},
                {"multi": true});
        }
    }
}

// Add empty array of tags when tags = null
db.getCollection("file").update({"tags": null}, {"$set": {"tags": []}}, {"multi": true});

// Drop dataset collection
db.getCollection("dataset").drop()

// Add new opencga administrator user to user collection #1425
var user = db.getCollection("user").findOne({"id": "opencga"});

if (user === null) {
    var metadata = db.getCollection("metadata").findOne({});
    db.getCollection("user").insert({
        "id": "opencga",
        "name": "opencga",
        "email": metadata["admin"]["email"],
        "password": metadata["admin"]["password"],
        "organization": "",
        "account": {
            "type": "ADMINISTRATOR",
            "creationDate": metadata["creationDate"],
            "expirationDate": "",
            "authOrigin": null,
            "authentication": {
                "id": "internal",
                "application": false
            }
        },
        "status": {
            "name": "READY",
            "date": metadata["creationDate"],
            "message": ""
        },
        "lastModified": metadata["creationDate"],
        "size": -1,
        "quota": -1,
        "projects": [],
        "tools": [],
        "configs": {
            "filters": []
        },
        "attributes": {}
    });
    // Remove from metadata collection the admin credentials
    db.getCollection("metadata").update({}, {"$unset": {"admin.email": "", "admin.password": ""}})
}

// Remove experiments,lastModified, cipher and tools from studies and users documents
db.getCollection("study").update({}, {"$unset": {"experiments": "", "lastModified": "", "cipher": ""}}, {"multi": true});
db.getCollection("user").update({}, {"$unset": {"tools": ""}}, {"multi": true});

// Ticket #1479 - TEXT -> STRING
function changeVariableType(variables) {
    for (var i in variables) {
        var variable = variables[i];
        if (variable["type"] === "TEXT") {
            variable["type"] = "STRING";
        }
        if (isNotUndefinedOrNull(variable.variableSet) && variable.variableSet.length > 0) {
            changeVariableType(variable.variableSet);
        }
    }
}

// Ticket #1479 - TEXT -> STRING
migrateCollection("study", {"variableSets" : { $exists: true, $ne: [] } }, {"variableSets": 1}, function(bulk, doc) {
    var setChanges = {};

    // Check variableSets
    if (isNotUndefinedOrNull(doc.variableSets) && doc.variableSets.length > 0) {
        for (var i in doc.variableSets) {
            changeVariableType(doc.variableSets[i].variables);
        }
        setChanges["variableSets"] = doc.variableSets;
    }

    if (Object.keys(setChanges).length > 0) {
        bulk.find({"_id": doc._id}).updateOne({"$set": setChanges});
    }
});


// TODO: Add indexes for new "deleted" collections
