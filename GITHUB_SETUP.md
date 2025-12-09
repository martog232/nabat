# How to Add Nabat Project to GitHub

## Step-by-Step Guide

### 1. Create a GitHub Repository

1. Go to https://github.com/new
2. Repository name: `nabat` (or your preferred name)
3. Description: "Real-time safety alert platform inspired by Citizen.com"
4. Choose **Public** or **Private**
5. **DO NOT** initialize with README, .gitignore, or license (we already have these)
6. Click **Create repository**

### 2. Initialize Git and Push (Run these commands)

Open Command Prompt in your project directory and run:

```bash
# Initialize Git repository
git init

# Add all files to staging
git add .

# Create first commit
git commit -m "Initial commit: Nabat safety alert platform with hexagonal architecture"

# Rename branch to main (if needed)
git branch -M main

# Add your GitHub repository as remote (replace YOUR-USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR-USERNAME/nabat.git

# Push to GitHub
git push -u origin main
```

### 3. If Using SSH Instead of HTTPS

Replace the remote URL with SSH:

```bash
git remote add origin git@github.com:YOUR-USERNAME/nabat.git
git push -u origin main
```

### 4. Verify on GitHub

Go to your repository URL:
```
https://github.com/YOUR-USERNAME/nabat
```

You should see all your files, including:
- README.md with project documentation
- Source code in proper structure
- Configuration files
- Database setup scripts

## Quick Commands Reference

```bash
# Check Git status
git status

# Add specific files
git add <file-name>

# Commit changes
git commit -m "Your commit message"

# Push changes
git push

# Pull latest changes
git pull

# View commit history
git log --oneline

# Create new branch
git checkout -b feature/your-feature-name
```

## What's Been Prepared

✅ `.gitignore` - Excludes build files, IDE files, logs, and sensitive data
✅ `README.md` - Complete project documentation with setup instructions
✅ All source code organized in hexagonal architecture
✅ Database setup scripts and documentation
✅ Configuration files for dev and prod environments

## Troubleshooting

### "git: command not found"
Install Git from: https://git-scm.com/download/win

### Authentication Failed
- For HTTPS: Use GitHub Personal Access Token (not password)
  - Generate token: GitHub → Settings → Developer settings → Personal access tokens
- For SSH: Set up SSH keys
  - Guide: https://docs.github.com/en/authentication/connecting-to-github-with-ssh

### Repository Already Exists
If you accidentally initialized with files:
```bash
git pull origin main --allow-unrelated-histories
git push -u origin main
```

## Best Practices

1. **Commit often** with meaningful messages
2. **Use branches** for new features
3. **Don't commit** sensitive data (passwords, API keys)
4. **Write descriptive** commit messages
5. **Pull before push** to avoid conflicts

## Example Workflow

```bash
# Start new feature
git checkout -b feature/user-authentication

# Make changes, then:
git add .
git commit -m "Add user authentication service"

# Push feature branch
git push -u origin feature/user-authentication

# Create Pull Request on GitHub, review, then merge
```

## GitHub Repository Settings (After Creation)

1. **Add topics**: `java`, `spring-boot`, `hexagonal-architecture`, `safety-alerts`, `websocket`
2. **Enable Issues** for bug tracking
3. **Add collaborators** if working in team
4. **Set up branch protection** for main branch (Settings → Branches)
5. **Add project description** and website URL

Your project is now ready for GitHub! 🚀

