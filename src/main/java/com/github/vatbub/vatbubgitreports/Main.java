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
import common.internet.Error;
import common.internet.Internet;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;
import reporting.GitHubIssue;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;


public class Main extends HttpServlet {
    private static final String gMailUsername = System.getenv("GMAIL_ISSUES_USERNAME");
    private static final String gMailPassword = System.getenv("GMAIL_ISSUES_PASSWORD");

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
            Internet.sendErrorMail("getWriter", "Unable not read request", e, gMailUsername, gMailPassword);
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
            Internet.sendErrorMail("ReadRequestBody", requestBody.toString(), e, gMailUsername, gMailPassword);
            return;
        }

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
            Internet.sendErrorMail("ParseJSON", requestBody.toString(), e, gMailUsername, gMailPassword);
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            response.setStatus(500);
            return;
        }

        // Authenticate on GitHub
        GitHubClient client = new GitHubClient();
        client.setOAuth2Token(System.getenv("GITHUB_ACCESS_TOKEN"));

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
        if (gitHubIssue.getThrowable()!=null){
            body = body + "Exception stacktrace:\n" + ExceptionUtils.getFullStackTrace(gitHubIssue.getThrowable()) + "\n";
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
        } catch (IOException e) {
            e.printStackTrace();
            Error error = new Error(e.getClass().getName() + " occurred while parsing the request", ExceptionUtils.getFullStackTrace(e));
            writer.write(gson.toJson(error));
            response.setStatus(500);
            Internet.sendErrorMail("ForwardToGitHub", requestBody.toString(), e, gMailUsername, gMailPassword);
            return;
        }

        writer.write(gson.toJson(issue));
    }
}
