# AGENTS.md

This file gives agent-specific instructions for working in this repository.

## Frontend Packaging Rule

Whenever a change touches frontend code under `modules/web/`, the served frontend must be rebuilt and copied into the server resources before producing or deploying a server JAR. The Legado server does not serve `modules/web/dist` directly; it serves the files embedded under `legado-server/src/main/resources/web/`.

Use this sequence from the repository root:

```bash
cd modules/web
npm run type-check
npm run test:mobile-layout
npm run test:mobile-activation
npm run test:reading-state
npm run test:cover-safety
npm run build-only

cd /home/allen/projects/legado
rsync -a --delete modules/web/dist/ legado-server/src/main/resources/web/

cd legado-server
./gradlew test shadowJar

cd /home/allen/projects/legado
sha256sum legado-server/build/libs/legado-server-all.jar
```

Important details:

- Do not run `./gradlew shadowJar` from the repository root; `shadowJar` belongs to the standalone `legado-server/` Gradle project.
- Commit the updated generated assets in `legado-server/src/main/resources/web/` together with the frontend source changes.
- If deploying to `allen@800g4`, upload the rebuilt JAR and replace the service JAR:

```bash
scp legado-server/build/libs/legado-server-all.jar allen@800g4:/tmp/legado-server-all.jar.new
ssh allen@800g4 'sha256sum /tmp/legado-server-all.jar.new'
```

Then on `800g4`:

```bash
sudo install -o legado -g legado -m 0644 /tmp/legado-server-all.jar.new /opt/legado-server/legado-server-all.jar
sudo systemctl restart legado-server
sha256sum /opt/legado-server/legado-server-all.jar
systemctl status legado-server --no-pager -l
```

After deployment, verify the browser is loading the new hashed frontend chunks from Nginx access logs.
