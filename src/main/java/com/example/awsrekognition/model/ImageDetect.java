package com.example.awsrekognition.model;

import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.rekognition.waiters.RekognitionWaiter;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageDetect {
    public static final Logger logger = Logger.getLogger(ImageDetect.class.getName());

    public static void main(String[] args){
        String modelArn = "arn:aws:rekognition:us-west-2:817790011668:project/PestDetection/version/PestDetection.2024-01-17T11.48.51/1705466932505";
        String projectArn = "arn:aws:rekognition:us-west-2:817790011668:project/PestDetection/1704180824437";
        Integer minInferenceUnits = 1;
        Integer maxInferenceUnits = null;
        String bucket = "custom-labels-console-us-west-2-43a4d04dbd";
        String key = "weevilsAndBees.jpg";
        String key1 = "weevilAndBee.jpeg";
        Region region = Region.US_EAST_1;

        try {
            System.setProperty("aws.accessKeyId","");
            System.setProperty("aws.secretAccessKey","");
            RekognitionClient rekClient = RekognitionClient.builder()
                    .credentialsProvider(SystemPropertyCredentialsProvider.create()) // Use profile credentials
                    .region(region)
                    .build();

            // Start the model.
            startMyModel(rekClient, projectArn, modelArn, minInferenceUnits, maxInferenceUnits);
            System.out.println(String.format("Model started: %s", modelArn));

            //S3 Image Detect Custom Labels
            detectS3ImageCustomLabels(rekClient, modelArn, bucket, key );
            detectS3ImageCustomLabels(rekClient, modelArn, bucket, key1 );

            //Stop the model.
            stopMyModel(rekClient, projectArn, modelArn);
            System.out.println(String.format("Model stopped: %s", modelArn));

            rekClient.close();

        } catch (RekognitionException rekError) {
            logger.log(Level.INFO, "Rekognition client error: {0}", rekError.getMessage());
            System.exit(1);
        } catch (Exception rekError) {
            logger.log(Level.INFO,"Error: {0}", rekError.getMessage());
            System.exit(1);
        }

    }

    public static int findForwardSlash(String modelArn, int n) {

        int start = modelArn.indexOf('/');
        while (start >= 0 && n > 1) {
            start = modelArn.indexOf('/', start + 1);
            n -= 1;
        }
        return start;

    }


    public static void startMyModel(RekognitionClient rekClient, String projectArn, String modelArn,
                                    Integer minInferenceUnits, Integer maxInferenceUnits
    ) throws Exception, RekognitionException {

        try {

            logger.log(Level.INFO, "Starting model: {0}", modelArn);

            StartProjectVersionRequest startProjectVersionRequest = null;

            if (maxInferenceUnits == null) {
                startProjectVersionRequest = StartProjectVersionRequest.builder()
                        .projectVersionArn(modelArn)
                        .minInferenceUnits(minInferenceUnits)
                        .build();
            }
            else {
                startProjectVersionRequest = StartProjectVersionRequest.builder()
                        .projectVersionArn(modelArn)
                        .minInferenceUnits(minInferenceUnits)
                        .maxInferenceUnits(maxInferenceUnits)
                        .build();

            }

            StartProjectVersionResponse response = rekClient.startProjectVersion(startProjectVersionRequest);

            logger.log(Level.INFO,"Status: {0}", response.statusAsString() );


            // Get the model version

            int start = findForwardSlash(modelArn, 3) + 1;
            int end = findForwardSlash(modelArn, 4);

            String versionName = modelArn.substring(start, end);


            // wait until model starts

            DescribeProjectVersionsRequest describeProjectVersionsRequest = DescribeProjectVersionsRequest.builder()
                    .versionNames(versionName)
                    .projectArn(projectArn)
                    .build();

            RekognitionWaiter waiter = rekClient.waiter();

            WaiterResponse<DescribeProjectVersionsResponse> waiterResponse = waiter
                    .waitUntilProjectVersionRunning(describeProjectVersionsRequest);

            Optional<DescribeProjectVersionsResponse> optionalResponse = waiterResponse.matched().response();

            DescribeProjectVersionsResponse describeProjectVersionsResponse = optionalResponse.get();

            for (ProjectVersionDescription projectVersionDescription : describeProjectVersionsResponse
                    .projectVersionDescriptions()) {
                if(projectVersionDescription.status() == ProjectVersionStatus.RUNNING) {
                    logger.log(Level.INFO,"Model is running" );

                }
                else {
                    String error = "Model training failed: " + projectVersionDescription.statusAsString() + " "
                            + projectVersionDescription.statusMessage() + " " + modelArn;
                    logger.log(Level.INFO,error);
                    throw new Exception(error);
                }

            }


        } catch (RekognitionException e) {
            logger.log(Level.SEVERE,"Could not start model: {}", e.getMessage());
            throw e;
        }

    }

    public static void stopMyModel(RekognitionClient rekClient, String projectArn, String modelArn)
            throws Exception, RekognitionException {

        try {

            logger.log(Level.INFO, "Stopping {0}", modelArn);

            StopProjectVersionRequest stopProjectVersionRequest = StopProjectVersionRequest.builder()
                    .projectVersionArn(modelArn).build();

            StopProjectVersionResponse response = rekClient.stopProjectVersion(stopProjectVersionRequest);

            logger.log(Level.INFO, "Status: {0}", response.statusAsString());

            // Get the model version

            int start = findForwardSlash(modelArn, 3) + 1;
            int end = findForwardSlash(modelArn, 4);

            String versionName = modelArn.substring(start, end);

            // wait until model stops

            DescribeProjectVersionsRequest describeProjectVersionsRequest = DescribeProjectVersionsRequest.builder()
                    .projectArn(projectArn).versionNames(versionName).build();

            boolean stopped = false;

            // Wait until create finishes

            do {

                DescribeProjectVersionsResponse describeProjectVersionsResponse = rekClient
                        .describeProjectVersions(describeProjectVersionsRequest);

                for (ProjectVersionDescription projectVersionDescription : describeProjectVersionsResponse
                        .projectVersionDescriptions()) {

                    ProjectVersionStatus status = projectVersionDescription.status();

                    logger.log(Level.INFO, "stopping model: {0} ", modelArn);

                    switch (status) {

                        case STOPPED:
                            logger.log(Level.INFO, "Model stopped");
                            stopped = true;
                            break;

                        case STOPPING:
                            Thread.sleep(5000);
                            break;

                        case FAILED:
                            String error = "Model stopping failed: " + projectVersionDescription.statusAsString() + " "
                                    + projectVersionDescription.statusMessage() + " " + modelArn;
                            logger.log(Level.SEVERE, error);
                            throw new Exception(error);

                        default:
                            String unexpectedError = "Unexpected stopping state: "
                                    + projectVersionDescription.statusAsString() + " "
                                    + projectVersionDescription.statusMessage() + " " + modelArn;
                            logger.log(Level.SEVERE, unexpectedError);
                            throw new Exception(unexpectedError);
                    }
                }

            } while (stopped == false);

        } catch (RekognitionException e) {
            logger.log(Level.SEVERE, "Could not stop model: {0}", e.getMessage());
            throw e;
        }

    }

    public static void detectS3ImageCustomLabels(RekognitionClient rekClient, String arn, String bucket, String key ) {

        try {
            S3Object s3Object = S3Object.builder()
                    .bucket(bucket)
                    .name(key)
                    .build();

            // Create an Image object for the source image
            Image s3Image = Image.builder()
                    .s3Object(s3Object)
                    .build();

            DetectCustomLabelsRequest detectCustomLabelsRequest = DetectCustomLabelsRequest.builder()
                    .image(s3Image)
                    .projectVersionArn(arn)
                    .maxResults(10)
                    .minConfidence(60f)
                    .build();

            DetectCustomLabelsResponse customLabelsResponse = rekClient.detectCustomLabels(detectCustomLabelsRequest);
            List<CustomLabel> customLabels = customLabelsResponse.customLabels();

            System.out.println(customLabels.toString());

            // Get the total number of custom labels
            List<CustomLabel> labels = customLabelsResponse.customLabels();
            int labelsCount = labels.size();
            System.out.println("Total custom labels detected: " + labelsCount);

            System.out.println("Detected labels for the given photo");
            for (CustomLabel customLabel: customLabels) {
                System.out.println(customLabel.name() + ": " + customLabel.confidence().toString());
            }

        } catch (RekognitionException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
}
