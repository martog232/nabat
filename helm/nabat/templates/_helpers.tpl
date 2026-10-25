{{- define "nabat.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "nabat.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Pull secrets for the images this chart pulls from GHCR.

Renders nothing when .Values.imagePullSecrets is empty, which is the right default when the
packages are public. GHCR packages published with GITHUB_TOKEN are *private* until made
public in package settings, and a private image with no pull secret gives ImagePullBackOff —
which --atomic then rolls back, so the failure surfaces as a timed-out release rather than an
authentication error.

Note the ordering problem if you go the private route: deploy.yml passes --create-namespace,
so on a genuinely clean cluster Helm creates the namespace and the pull secret cannot already
be in it. Create the namespace and the secret out of band first, or make the packages public.
*/}}
{{- define "nabat.imagePullSecrets" -}}
{{- with .Values.imagePullSecrets }}
imagePullSecrets:
{{- range . }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{- define "nabat.labels" -}}
app.kubernetes.io/name: {{ include "nabat.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Container-level hardening applied to every container in this chart.

It says nothing about *which* user the process runs as — that is a property of the image,
so it lives per component in values.yaml as `podSecurityContext`, next to the `image` it
was read off. What is here is the part that is true regardless of image: a process that
cannot gain privileges it did not start with, holds no Linux capabilities at all, and runs
under the runtime's default seccomp filter.

Dropping ALL capabilities is safe for every workload here because none of them bind a port
below 1024 (Kong proxies on 8000, the applications on 8080/8081) and none manage devices or
network interfaces. Promtail is the one exception and does not use this helper — see the
comment in promtail.yaml.

`readOnlyRootFilesystem` is deliberately absent. Every JVM here writes hsperfdata to /tmp,
Postgres writes its socket to /var/run/postgresql, and Kong writes its prefix directory;
turning it on means an emptyDir per writable path, which is worth doing but is a larger
change than this one and would fail at runtime rather than at render time.
*/}}
{{- define "nabat.containerSecurityContext" -}}
allowPrivilegeEscalation: false
capabilities:
  drop:
  - ALL
seccompProfile:
  type: RuntimeDefault
{{- end }}
