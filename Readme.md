# VatbubGitReports
This project is inspired by the website [GitReports](gitreports.com) which allows users to post issues on GitHub without creating a GitHub account. This lets any user of your app report bugs and feature requests in no time.
The problem with GitReports was however that it randomly returned 404 errors which is why I decided to reimplement it.

## How it works
The official [GitHub API](https://developer.github.com/) requires requests to be authenticated or else they won't pass through. This software acts like a proxy, it forwards anonymous requests to the GitHub issue api but adds a authentication token to it prior to forwarding it. When installing this software on your server, you need to provide it with your GitHub AccessToken which will cause issues to appear under your name.

## What it can do
Currently, we only support anonymous calls to the GitHub issue api and only in a very limited manner. Users can specify an author name, their email address, an issue title and body and attach urls to log files and/or a screenshot.

## What it can't do
Anything else with the GitHub api and that is for a reason. We believe that any other modifications in a GitHub repo should be trackable and this can only be achieved by authenticated requests.

## Use it for your projects
As we didn't implement any frontend yet, you need to run your own server with this software. But don't be afraid, it is actually quite simple.

### Requirements
- Any server that is accessible through the internet and runs the [Apache TomCat Server v8](http://tomcat.apache.org/). We recommend a free account at [Heroku](https://www.heroku.com/home)
- A http client that sends the requests to the server. If you want a ready-to-use java-library, you can use our [common](https://github.com/vatbub/common)-library that offers you a full UI to send requests from.
- [Apache Maven](https://maven.apache.org/) if you *don't* plan to run your instance on Heroku

## Get your GitHub API Access Key
As we did not implement OAuth 2 yet, you need to get your Access Key manually.
1. Log in on GitHub with the GitHub account that should appear as the issue author on GitHub. It is important that this user has write permissions to the repositories you want to post issues to, that means the user must be either the owner of the repository or a [collaborator](https://help.github.com/articles/inviting-collaborators-to-a-personal-repository/).
2. Go to the [Personal access tokens settings page](https://help.github.com/articles/inviting-collaborators-to-a-personal-repository/)
3. Click on `Generate new token`
4. Give the token a good description and give it all `repo`-permissions (`repo:status`, `repo_deployment`, `public_repo`)
5. Click on `Generate token` and copy the generated token to a secret place where you can find it again but nobody else can. Keep in mind that this token allows anyone who knows it to do anything in your repositories.

## Install it on Heroku
1. [Create a free account on Heroku](https://signup.heroku.com/login)
2. [Create a new app](https://dashboard.heroku.com/new) in the Heroku Dashboard
3. [Download the heroku cli](https://devcenter.heroku.com/articles/heroku-cli)
4. [Clone this repo](https://help.github.com/articles/cloning-a-repository/)
5. Run the command `heroku git:remote -a <appname>` where `<appname>` is the name of the app you created on Heroku
6. Create a environment variable for your GitHub token you created before using the heroku cli: `heroku config:set GITHUB_ACCESS_TOKEN=<token>` where `<token>` is the GitHub token you created earlier.
7. Now deploy and run the app on Heroku using the command `git push heroku master`.
8. Once the command finished, run `heroku open`. This will open the very basic frontend of the application of your web browser. If everything worked, you should see a simple "Hello World"-message. Note the url your browser shows, this is the base url of your instance.

## Install it on any other TomCat server
1. Create a new environment variable on your system that is called `GITHUB_ACCESS_TOKEN`. Its value is the GitHub API token you created earlier.
2. Run `mvn package` to get the application packaged into a single `*.war`-file.
3. Now open the `/target/`-folder and copy the file called `vatbubgitreports.war`.
4. Open the folder of your TomCat installation and go into the subfolder called `webapps` and restart the server.
5. The base url of your instance will be `http://host:port/vatbubgitreports`

## Contributing
Contributions of any kind are very welcome. Just fork and submit a Pull Request and we will be happy to merge. Just keep in mind that we use [Issue driven development](https://github.com/vatbub/defaultRepo/wiki/Issue-driven-development).
