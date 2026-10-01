# Deploy RaktSetu free: Render + Neon

You get a public link such as `https://raktsetu.onrender.com`. Cost: ₹0, no card. Takes about 20 minutes.

- **Render** runs the app. Free plan: it sleeps after 15 minutes without visitors, and the next visit takes about a
  minute to wake it. Open the link a few minutes before you need it.
- **Neon** holds the database. The free plan is permanent: 0.5 GB, far more than RaktSetu needs.

## Before you start

Merge the security pull request (it contains the `prod` settings for running behind Render's proxy) and this one.

## 1. Create the database on Neon

1. Go to https://neon.com and sign up (GitHub sign-in works).
2. Create a project: name `raktsetu`, region **AWS Asia Pacific (Singapore)**, PostgreSQL 17.
3. On the project dashboard, click **Connect**. Turn **Connection pooling off** (RaktSetu keeps only 5 connections, and
   the pooled address can break the database set-up that runs at start-up). Copy these values:
   - **Host**, for example `ep-cool-name-123456.ap-southeast-1.aws.neon.tech` (without `-pooler`)
   - **Database**: usually `neondb`
   - **Role** (user name) and **Password**

Build the database address from them like this (no user name or password inside it):

```
jdbc:postgresql://HOST/DATABASE?sslmode=require
```

For example: `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`

## 2. Deploy the app on Render

1. Go to https://render.com and sign up with **GitHub**.
2. Click **New → Blueprint**, allow Render to see the `raktsetu` repository, and select it.
3. Render reads `render.yaml` and asks for these values:

   | Setting | What to enter |
   |---|---|
   | `RAKTSETU_DB_URL` | The `jdbc:postgresql://...` address from step 1 |
   | `RAKTSETU_DB_USER` | Neon's role |
   | `RAKTSETU_DB_PASSWORD` | Neon's password |
   | `RAKTSETU_PRIVACY_ORGANISATION` | Who runs the network, e.g. `RaktSetu demonstration (MPSTME)` |
   | `RAKTSETU_PRIVACY_CONTACT` | An e-mail address you check |

4. Click **Apply**. The first build takes about 10 minutes (it builds the Java code and the website).
5. When the service shows **Live**, open its link (top of the service page).

## 3. Straight away: create the administrator

The link is public, and **the first person to open it creates the administrator**. Open it yourself at once and
complete the setup page. Then add facilities and people as usual.

## Updating

Every merge to `main` rebuilds and redeploys automatically. Database changes are applied on start-up by the
migrations, and the data in Neon is kept.

## If something goes wrong

| Problem | Fix |
|---|---|
| Build fails | Render → the service → **Events** → open the failed deploy's log |
| "Application failed to respond" | Check the three database settings; the address must start with `jdbc:postgresql://` and end with `?sslmode=require` |
| First page takes a minute | Normal: the free plan was asleep |
| Database stops for the rest of the month | Neon's free plan allows about 400 hours of database time a month. RaktSetu uses it only while Render is awake, so normal demo use stays well inside it; don't use an uptime monitor to keep the app awake all month |
| Forgot the admin password | Ask another administrator to reset it; if there is none, delete and recreate the Neon project to start again |
