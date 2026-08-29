# syntax=docker/dockerfile:1

# ============================================================================
#  Stage 1 - build
# ============================================================================
# Same principle as the API image: Node builds the bundle and then does not ship. The output
# of `vite build` is static files - HTML, JS, CSS. Serving them needs a web server, not a
# JavaScript runtime, so the 200 MB Node image has no business in the final layer.

# TODO 1 - the build stage.
    FROM node:24-alpine AS build
    WORKDIR /build
#
#   Then the same cache-ordering trick the API image uses, and for the same reason: copy the
#   dependency manifests, install, and only THEN copy the source. Editing a component should
#   not re-run npm install.
    COPY package.json package-lock.json ./
    RUN npm ci
    COPY . .
    RUN npm run build          # -> /build/dist
#
#   npm ci, not npm install - identical reasoning to the CI workflow. It installs exactly
#   what the lockfile pins and fails if the lock and package.json disagree.


# ============================================================================
#  Stage 2 - serve
# ============================================================================
# TODO 2 - the runtime stage.
    FROM nginx:alpine
   #   Copy the built bundle out of the build stage into nginx's document root:
    COPY --from=build /build/dist /usr/share/nginx/html
   #   And replace nginx's stock config with ours - that file is where the SPA fallback lives:
    COPY nginx.conf /etc/nginx/conf.d/default.conf
#
    EXPOSE 80
#   nginx:alpine already has a correct CMD, so do not add one.
#
#   Size worth noticing when it builds: nginx:alpine is ~25 MB against the API image's
#   ~111 MB base. The two halves of this application have wildly different runtime costs,
#   which is exactly the argument for deploying them as separate units.


# ----------------------------------------------------------------------------
#  Why there is no VITE_API_BASE_URL build arg here
# ----------------------------------------------------------------------------
# Every fetch in this app uses a RELATIVE path: fetch('/api/v1/customers'). That resolves
# against whatever origin served the page, which is Vite in development and CloudFront in
# production. It only works if something answers /api on that same origin.
#
# DECIDED (see AGENTS.md): CloudFront routes /api/* to the API origin as a second behaviour.
# The two halves are therefore same-origin in production, relative paths keep working, and
# THIS IMAGE STAYS ENVIRONMENT-INDEPENDENT - one artifact promoted from staging to
# production rather than one build per environment.
#
# The rejected alternative was a build-time base URL. Vite inlines import.meta.env at build
# time, so the URL would be baked into the bundle and staging could never be the same image
# as production. That is a real loss: it means the thing you tested is not the thing you
# ship.
#
# Consequence to keep in mind: the API's CORS config is now defence in depth rather than
# load-bearing. It stays - it costs nothing, documents which origins are legitimate, and
# still protects the API if it is ever reached directly instead of through the distribution.
