// Load the SDK for JavaScript
var AWS = require('aws-sdk');
// Set the region 
AWS.config.update({region: 'us-east-1'});

// const AWS = require('aws-sdk');
// const docClient = new AWS.DynamoDB.documentClient({region :'us-east-1'})
// Create DynamoDB document client
var docClient = new AWS.DynamoDB.DocumentClient({apiVersion: '2012-08-10'});

exports.handler = function(e, context, callback) {
    // TODO implement

    var params = {
        Item: {
            device_name : e.device,
            timestamp : Date.now().toString(),
            humidity : e.humidity,
            temp : e.temp.toString(),
            light : e.light,
        },
        
        TableName : 'gardenTable'

    };
    docClient.put(params, function(err, data){
        if(err){
            callback(err, null);
        }else{
            callback(null, data);
        }
    });
    // context.done(null, 'Hello from Lambda');
};
