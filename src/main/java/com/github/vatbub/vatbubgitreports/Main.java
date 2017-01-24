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
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;
import reporting.GitHubIssue;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

public class Main extends HttpServlet {
    private static Properties properties = null;

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
        }
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        // load properties
        if (properties == null) {
            properties = new Properties();
            try {
                properties.load(getClass().getResourceAsStream("/application.properties"));
            } catch (IOException e) {
                sendErrorMail("readProperties", "Unable not read application properties", e);
                e.printStackTrace();
                return;
            }
        }

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
            return;
        }

        // Authenticate on GitHub
        GitHubClient client = new GitHubClient();
        client.setOAuth2Token(properties.getProperty("GitHub_AccessToken"));

        // Convert the isue object
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
        if (gitHubIssue.getLogLocation()!=null) {
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
            new IssueService(client).createIssue(gitHubIssue.getToRepo_Owner(), gitHubIssue.getToRepo_RepoName(), issue);
            res.passed = true;
        } catch (IOException e) {
            // Will only happen if I did a typo above
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            sendErrorMail("ForwardToIFTTT", requestBody.toString(), e);
            return;
        }

        // Tell IFTTT the result of the operation
        writer.write(gson.toJson(res));
    }

    private void sendErrorMail(String phase, String requestBody, Throwable e) {
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
            message.setSubject("[iftttEventFilter] An error occurred in your application");
            message.setText("Exception occurred in phase: " + phase + "\n\nRequest that caused the exception:\n" + requestBody
                    + "\n\nStacktrace of the exception:\n" + ExceptionUtils.getFullStackTrace(e));

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
