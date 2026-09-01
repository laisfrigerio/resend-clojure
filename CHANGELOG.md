# Changelog

## [1.1.0] - 2026-09-01

- Updating changelog.md file

## [1.0.1] - 2026-08-26

- Adding a default api-key overloading
- Updating readme.md with the new api-key overloading implementation
- Replacing resend-clojure namespace to just resend
- Removing files within resend-clojure namespace

## [0.1.3] - 2026-08-26

- Use Admin PAT to authenticate Git to enable release.yaml workflow commit at main branch

- Adds the GitHub app to run the `release.yaml` workflow. This will allow only that app to commit to the `main` branch, maintaining the branch restriction configuration (merges only via PR).

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/lang/pt-BR/).
