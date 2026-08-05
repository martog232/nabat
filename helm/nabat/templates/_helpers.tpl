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
