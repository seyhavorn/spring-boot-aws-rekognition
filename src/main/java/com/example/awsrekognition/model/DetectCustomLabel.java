package com.example.awsrekognition.model;

import com.amazonaws.services.rekognition.model.DescribeProjectVersionsResult;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.waiters.RekognitionWaiter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.awsrekognition.model.ImageDetect.findForwardSlash;

public class DetectCustomLabel {

    public static final Logger logger = Logger.getLogger(DetectCustomLabel.class.getName());

    private static transient BufferedImage image;
    private static transient DetectCustomLabelsResponse response;
    private static transient Dimension dimension;

    public static void main(String[] args) throws Exception {

        final String USAGE = "\n" +
                "Usage: " +
                "DetectLabels <project arn> <S3 bucket> <S3 key>\n\n" +
                "Where:\n" +
                "project arn - the arn of the model in Rekognition Custom Labels to the image (for example, arn:aws:rekognition:us-east-1:XXXXXXXXXXXX:project/YOURPROJECT/version/YOURPROJECT.YYYY-MM-DDT00.00.00/1234567890123). \n" +
                "S3 bucket - the bucket where your image is stored (for example, my-bucket-name \n" +
                "S3 key - the path of the image inside your bucket (for example, myfolder/pic1.png). \n\n";

//        if (args.length != 3) {
//            System.out.println(USAGE);
//            System.exit(1);
//        }
        System.setProperty("aws.accessKeyId","");
        System.setProperty("aws.secretAccessKey","");

//        String arn = "arn:aws:rekognition:us-east-1:388694498119:project/dogcat/version/dogcat.2023-11-21T10.33.24/1700537605130";
//        String arn = "arn:aws:rekognition:us-east-1:388694498119:project/pest_classify/version/pest_classify.2023-11-12T23.14.27/1699805668458";
        String modelArn = "arn:aws:rekognition:us-west-2:817790011668:project/PestDetection/version/PestDetection.2024-01-19T09.47.58/1705632479370";
        String projectArn = "arn:aws:rekognition:us-west-2:817790011668:project/PestDetection/1704180824437";
        String bucket = "custom-labels-console-us-west-2-43a4d04dbd";
        String key = "weevilsAndBees.jpg";
        Integer minInferenceUnits = 1;
        Integer maxInferenceUnits = null;
        Region region = Region.US_WEST_2;
        RekognitionClient rekClient = RekognitionClient.builder()
                .region(region)
                .build();

        startMyModel(rekClient, projectArn, modelArn, minInferenceUnits, maxInferenceUnits);
        System.out.println(String.format("Model started: %s", modelArn));
        //S3 Image Detect Custom Labels
        detectImageCustomLabels(rekClient, modelArn, bucket, key );
        stopMyModel(rekClient, projectArn, modelArn);
        rekClient.close();
    }

    public static void detectImageCustomLabels(RekognitionClient rekClient, String arn, String bucket, String key) throws IOException,RekognitionException{

        try {
            //local image detect
            String photo = "data/test/wasp4.jpeg";
            InputStream sourceStream = new FileInputStream(new File(photo));
            SdkBytes imageBytes = SdkBytes.fromInputStream(sourceStream);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes.asByteArray());
            image = ImageIO.read(inputStream);
            System.out.println(image.createGraphics());
            software.amazon.awssdk.services.rekognition.model.Image localImageBytes = Image.builder().bytes(imageBytes).build();
            //*************************

            //**********S3 Storage***********
//            S3Object s3Object = S3Object.builder()
//                    .bucket(bucket)
//                    .name(key)
//                    .build();
//
//            // Create an Image object for the source image
//            Image s3Image = Image.builder()
//                    .s3Object(s3Object)
//                    .build();
            //*************************

            DetectCustomLabelsRequest detectCustomLabelsRequest = DetectCustomLabelsRequest.builder()
                    .image(localImageBytes)
                    .projectVersionArn(arn)
                    .maxResults(10)
                    .minConfidence(60f)
                    .build();

            //change response to suit for local photo
            DetectCustomLabelsResponse response = rekClient.detectCustomLabels(detectCustomLabelsRequest);
            java.util.List<CustomLabel> customLabels = response.customLabels();

//            DetectCustomLabelsResponse customLabelsResponse = rekClient.detectCustomLabels(detectCustomLabelsRequest);
//            List<CustomLabel> customLabels = customLabelsResponse.customLabels();

            //System.out.println(customLabels.toString());
            System.out.println(response.toString());

            // Get the total number of custom labels
            java.util.List<CustomLabel> labels = response.customLabels();
            int labelsCount = labels.size();
            System.out.println("Total custom labels detected: " + labelsCount);

            System.out.println("Detected labels for the given photo");
            for (CustomLabel customLabel: customLabels) {
                System.out.println(customLabel.name() + ": " + customLabel.confidence().toString());
            }

            // Draw bounding boxes
            drawLabels(response);

        } catch (RekognitionException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void drawLabels(DetectCustomLabelsResponse response){
        int boundingBoxBorderWidth = 5;
        int imageHeight = image.getHeight();
        int imageWidth = image.getWidth();

        // Set up drawing
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.GREEN);
        g2d.setFont(new Font("Tahoma", Font.PLAIN, 50));
        Font font = g2d.getFont();
        FontRenderContext frc = g2d.getFontRenderContext();
        g2d.setStroke(new BasicStroke(boundingBoxBorderWidth));

        List<CustomLabel> customLabels = response.customLabels();

        int imageLevelLabelHeight = 0;
        for (CustomLabel customLabel : customLabels) {

            String label = customLabel.name();

            int textWidth = (int) (font.getStringBounds(label, frc).getWidth());
            int textHeight = (int) (font.getStringBounds(label, frc).getHeight());

            // Draw bounding box, if present
            if (customLabel.geometry() != null) {

                BoundingBox box = customLabel.geometry().boundingBox();
                float left = imageWidth * box.left();
                float top = imageHeight * box.top();
                System.out.println(box.toString());
                // Draw black rectangle
                g2d.setColor(Color.BLACK);
                g2d.fillRect(Math.round(left + (boundingBoxBorderWidth)), Math.round(top + (boundingBoxBorderWidth)),
                        textWidth + boundingBoxBorderWidth, textHeight + boundingBoxBorderWidth);

                // Write label onto black rectangle
                g2d.setColor(Color.GREEN);
                g2d.drawString(label, left + boundingBoxBorderWidth, (top + textHeight));

                // Draw bounding box around label location
                g2d.drawRect(Math.round(left), Math.round(top), Math.round((imageWidth * box.width())),
                        Math.round((imageHeight * box.height())));
            }
            // Draw image level labels.
            else {
                // Draw black rectangle
                g2d.setColor(Color.BLACK);
                g2d.fillRect(10, 10 + imageLevelLabelHeight, textWidth, textHeight);
                g2d.setColor(Color.GREEN);
                g2d.drawString(label, 10, textHeight + imageLevelLabelHeight);

                imageLevelLabelHeight += textHeight;
            }

        }
        g2d.dispose();
        System.out.println(image.getWidth() + " " + image.getHeight());
        // Save the image to the specified output path
        try {
            String name = "weevil"; // custom name
            ImageIO.write(image, "png", new File("data/test/"+ name +"CustomLabels.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void startMyModel(RekognitionClient rekClient, String projectArn, String modelArn,
                                    Integer minInferenceUnits, Integer maxInferenceUnits
    ) throws Exception, RekognitionException {

        try {

            logger.log(Level.INFO, "Starting model: {0}", modelArn);

            StartProjectVersionRequest startProjectVersionRequest = null;
//            String status = getStatus(modelArn,projectArn,rekClient);


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
            // Get the model version



        } catch (RekognitionException e) {
            logger.log(Level.SEVERE,"Could not start model: {}", e.getMessage());
            throw e;
        }

    }

//    public static String getStatus(String modelArn, String projectArn, RekognitionClient rekClient) throws Exception {
//
//        int start = findForwardSlash(modelArn, 3) + 1;
//        int end = findForwardSlash(modelArn, 4);
//
//        String versionName = modelArn.substring(start, end);
//
//        DescribeProjectVersionsRequest request = new DescribeProjectVersionsRequest()
//                .withProjectArn(projectArn)
//                .withVersionNames(versionName);
//        DescribeProjectVersionsResult result = rekClient.describeProjectVersions(request);
//
//        for (ProjectVersionDescription model : result.getProjectVersionDescriptions()) {
//            String status = model.getStatus();
//            // ... (process model status)
//        }
//    }

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
}
