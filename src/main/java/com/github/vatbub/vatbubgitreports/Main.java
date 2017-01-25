package com.github.vatbub.vatbubgitreports;

/*-
 * #%L
 * vatbubgitreports Maven Webapp
 * %%
 * Copyright (C) 2016 - 2017 Frederik Kammel
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.Common;
import logging.FOKLogger;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import reporting.GitHubIssue;
import view.reporting.ReportingDialog;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;

public class Main extends HttpServlet {
    private static URL gitHubApiURL;

    static {
        Common.setAppName("vatbubgitreports");
        try {
            gitHubApiURL = new URL("https://api.github.com/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Print a basic page
            response.getWriter().println("<html>\n" +
                    "<body>\n" +
                    "<h2>Hello World!</h2>\n" +
                    "</body>\n" +
                    "</html>");
        } catch (IOException e) {
            e.printStackTrace();
            response.setStatus(500);
        }
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        PrintWriter writer;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        StringBuilder requestBody = new StringBuilder();
        String line;

        try {
            writer = response.getWriter();
        } catch (IOException e) {
            sendErrorMail("getWriter", "Unable not read request", e);
            e.printStackTrace();
            response.setStatus(500);
            return;
        }

        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        } catch (IOException e) {
            Error error = new Error(e.getClass().getName() + " occurred while reading the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            response.setStatus(500);
            sendErrorMail("ReadRequestBody", requestBody.toString(), e);
            return;
        }
        Passed res = new Passed();

        // parse the request
        if (!request.getContentType().equals("application/json")) {
            // bad content type
            Error error = new Error("content type must be application/json");
            writer.write(gson.toJson(error));
        }

        GitHubIssue gitHubIssue;

        try {
            System.out.println("Received request:");
            System.out.println(requestBody.toString());
            System.out.println("Request encoding is: " + request.getCharacterEncoding());
            gitHubIssue = gson.fromJson(requestBody.toString(), GitHubIssue.class);
        } catch (Exception e) {
            sendErrorMail("ParseJSON", requestBody.toString(), e);
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            response.setStatus(500);
            return;
        }

        // Convert the issue object
        Issue issue = new Issue();
        issue.setTitle(gitHubIssue.getTitle());
        String body = "";
        boolean metadataGiven = false;
        if (!gitHubIssue.getReporterName().equals("")) {
            body = "Reporter name: " + gitHubIssue.getReporterName() + "\n";
            metadataGiven = true;
        }
        if (!gitHubIssue.getReporterEmail().equals("")) {
            body = body + "Reporter email: " + gitHubIssue.getReporterEmail() + "\n";
            metadataGiven = true;
        }
        if (gitHubIssue.getLogLocation() != null) {
            body = body + "Log location: " + gitHubIssue.getLogLocation() + "\n";
            metadataGiven = true;
        }
        if (metadataGiven) {
            body = body + "----------------------------------" + "\n\nDESCRIPTION:\n";
        }
        body = body + gitHubIssue.getBody();

        issue.setBody(body);

        // send the issue to GitHub
        try {
            URL finalGitHubURL = new URL(gitHubApiURL.toString() + "/repos/" + gitHubIssue.getToRepo_Owner() + "/" + gitHubIssue.getToRepo_RepoName());
            try {
                //HttpURLConnection connection = (HttpURLConnection) finalGitHubURL.openConnection();

                // build the request body
                String query = gson.toJson(issue);

                // request header
                /*connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Content-Encoding", "UTF-8");
                connection.setRequestProperty("Content-Length", Integer.toString(query.length()));
                connection.setRequestProperty("Authorization", "token " + properties.getProperty("GitHub_AccessToken"));
                connection.setDoOutput(true);
                connection.getOutputStream().write(query.getBytes("UTF8"));
                connection.getOutputStream().flush();
                connection.getOutputStream().close();*/

                /*Request gitHubRequest = Request.Post(finalGitHubURL.toString())
                        .addHeader("Authorization", "token " + properties.getProperty("GitHub_AccessToken"))
                        .bodyString(query, ContentType.APPLICATION_JSON);
                Response gitHubResponse = gitHubRequest.execute();*/

                System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.SimpleLog");
                System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
                System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");

                CloseableHttpClient httpClient = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost(finalGitHubURL.toString());
                httpPost.addHeader("Authorization", "token " + System.getenv("GITHUB_ACCESS_TOKEN"));
                HttpEntity entity = new ByteArrayEntity(query.getBytes("UTF-8"));
                httpPost.setEntity(entity);

                int responseCode;
                String gitHubResponseAsString;
                try (CloseableHttpResponse gitHubResponse = httpClient.execute(httpPost)) {
                    // check the server response
                    HttpEntity gitHubResponseEntity = gitHubResponse.getEntity();
                    responseCode = gitHubResponse.getStatusLine().getStatusCode();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    gitHubResponse.getEntity().writeTo(os);
                    gitHubResponseAsString = new String(os.toByteArray(),"UTF-8");
                    // responseBody = EntityUtils.toString(gitHubResponseEntity);
                    EntityUtils.consume(gitHubResponseEntity);
                }

                response.setStatus(responseCode);

                FOKLogger.info(ReportingDialog.class.getName(), "Submitted GitHub issue, response code from VatbubGitReports-Server: " + responseCode);
                FOKLogger.info(ReportingDialog.class.getName(), "Response from Server:\n" + gitHubResponseAsString);

                if (responseCode >= 400) {
                    // something went wrong
                    sendErrorMail("ForwardToGitHub", requestBody.toString(), "Response from GitHub (Status " + responseCode + "):\n" + gitHubResponseAsString);
                }
            } catch (IOException e) {
                FOKLogger.log(ReportingDialog.class.getName(), Level.SEVERE, "An error occurred", e);
            }
            res.passed = true;
        } catch (IOException e) {
            e.printStackTrace();
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            response.setStatus(500);
            sendErrorMail("ForwardToGitHub", requestBody.toString(), e);
            return;
        }

        writer.write(gson.toJson(res));
    }

    private void sendErrorMail(String phase, String requestBody, Throwable e) {
        sendErrorMail(phase, requestBody, "Stacktrace of the exception:" + ExceptionUtils.getFullStackTrace(e));
    }

    private void sendErrorMail(String phase, String requestBody, String errorInfoMessage) {
        final String username = "vatbubissues@gmail.com";
        final String password = "cfgtzhbnvfcdre456780uijhzgt67876ztghjkio897uztgfv";
        final String toAddress = "vatbub123+automatederrorreports@gmail.com";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(toAddress));
            message.setSubject("[vatbubgitreports] An error occurred in your application");
            message.setText("Exception occurred in phase: " + phase + "\n\nRequest that caused the exception:\n" + requestBody
                    + "\n\n" + errorInfoMessage);

            Transport.send(message);

            System.out.println("Sent email with error message to " + toAddress);

        } catch (MessagingException e2) {
            throw new RuntimeException(e2);
        }
    }

    class Passed {
        boolean passed;
    }

    class Error {
        String error;
        String stacktrace;

        Error(String error) {
            this(error, "");
        }

        Error(String error, String stacktrace) {
            this.error = error;
            this.stacktrace = stacktrace;
        }
    }
}
