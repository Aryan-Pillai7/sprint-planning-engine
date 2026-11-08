# AI-Assisted Sprint Planning Engine

## Project goals (fixed, do not drift)

This is a Spring Boot (Java) Agile/Scrum backend. Only these four features exist:

1. Create, retrieve, and update user stories and tasks. Stories and tasks are stored in MySQL now. ServiceNow sync comes later.
2. AI-assisted story-point estimates, with team review and override before a sprint is committed. The estimation provider comes later. Build the review and override flow around an interface.
3. Strategy design pattern for interchangeable capacity-based and workload-balanced developer-assignment algorithms.
4. Normalized MySQL schema with Spring Data JPA, with indexes on frequently queried columns. JUnit and Mockito tests, measured with JaCoCo, run in GitHub Actions CI.

Follow through on these goals. Do not add features outside them.

Do not add a frontend. Do not add authentication.

## Explicitly out of scope for this iteration

- ServiceNow integration and its credentials. Do not write any ServiceNow client code.
- Gemini integration and its credentials. Do not write any Gemini client code.
- Docker. Use the locally installed MySQL 8.
- Any docs folder, README, or extra documentation files.

## Credentials

- `.env.example` in the repo root holds placeholders only.
- Use `DB_PASSWORD` in `application.yml` through an environment variable: `${DB_PASSWORD}`.
- `.env` must stay in `.gitignore`.
- Do not add ServiceNow or Gemini entries to `.gitignore`. The owner adds those.

## Git

- Commit and push directly to `main`. Do not create pull requests.
- Make as many commits as needed, with clear messages, and push freely.
- Do not force-push, rewrite history, or delete branches. If something seems risky, stop and tell the owner.
- Every commit must be authored only by Aryan-Pillai7 <aryanpx.007@gmail.com>, the identity GitHub links to the account. Do not add `Co-Authored-By` or any Claude attribution lines to commits or other messages.
- Use the repository already in the working directory. If it has no `origin` remote, stop and tell the owner.

## Docker and file location

- The whole project lives on D:. Do not place anything on C:.
- Do not pull any Docker images. Use the local MySQL.

## File structure

- Keep the file count low and the structure obvious to a junior developer. Put related code in fewer files, and do not create a file for a single trivial function.
- Use plain folder names such as `backend/`, not deep nested layouts.
- Keep Maven's `backend/src/main/java` and `backend/src/test/java`. Do not add other nested folders beyond what Maven needs.
- Keep the number of Java files small. Use one file for related entities where Java allows it, and group repositories, services, and controllers sensibly.

```
repo/
  backend/             Spring Boot app, Maven wrapper, pom.xml, Maven layout
  .github/workflows/   CI workflow
  .env.example
  .gitignore
  .gitattributes
  CLAUDE.md
```

## Working principles

- Think before coding. State assumptions. If something is unclear, make the most reasonable choice and say what you chose.
- Simplicity first. Write the minimum code that solves the problem. No speculative abstractions or configurability.
- Surgical changes. Touch only what the task needs.
- Goal-driven execution. Define what success looks like, then verify it by running the build and tests.
