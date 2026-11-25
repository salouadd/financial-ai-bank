# README19 — Préparer et déployer Identity et Financial Coach sur Docker Swarm

Ce guide transforme le questionnaire `README8.md` en plan de travail. Remplissez les fiches avec les équipes, puis préparez les images, les secrets, les réseaux, les dépendances et enfin la stack.

## 0. État déjà vérifié dans les deux dépôts

Les dépôts locaux ont été contrôlés et les adaptations suivantes ont été appliquées :

- Identity écoute sur le port `8081`.
- Financial Coach écoute sur le port `8082`.
- Identity publie `GET /.well-known/jwks.json`.
- Identity émet les JWT RS256 avec les claims `sub`, `userId`, `role`, `status` et `aud`.
- Identity publie les routes `POST /users/register`, `POST /users/login` et `GET /users/me`.
- Financial Coach accepte maintenant le claim `userId` avant de faire un repli sur `sub`.
- Identity peut recevoir `IDENTITY_JWT_AUDIENCE`; elle doit avoir exactement la même valeur que `JWT_AUDIENCE` côté coach.
- Identity doit recevoir `IDENTITY_JWT_ISSUER`; cette valeur doit être exactement égale à `JWT_ISSUER` côté coach.
- Les deux stacks peuvent maintenant joindre le même réseau Gateway externe si `GATEWAY_OVERLAY_NETWORK` est défini identiquement.

Points à compléter avant la production :

- construire et pousser les deux images ;
- fournir des clés RSA persistantes à Identity dans des secrets Swarm ;
- fournir les mots de passe PostgreSQL dans des secrets ;
- définir les noms DNS internes définitifs ;
- sauvegarder les bases avant chaque migration ;
- déployer Identity avant de tester Financial Coach.

Règle essentielle : Docker Swarm **ne construit pas** les images. Chaque image doit être construite puis poussée vers un registre avant `docker stack deploy`.

## 1. Informations à collecter

### Fiche Identity Service

| Élément | Réponse |
|---|---|
| Responsable / équipe | |
| Dépôt et version Git | |
| Nom complet de l’image | |
| Tag immuable ou digest | |
| Image construite et poussée ? | Oui / Non — par : — date/version : |
| Tous les nœuds Swarm peuvent tirer l’image ? | Oui / Non — précisions : |
| Registre privé ? | Oui / Non |
| Authentification au registre | |
| Port interne | |
| Écoute sur `0.0.0.0` | Oui / Non — preuve : |
| Health/liveness | |
| Readiness | |
| Routes Identity | |
| Variables d’environnement | |
| Base de données / stockage | |
| Secrets | |
| Ressources CPU/mémoire | |
| Politique de redémarrage | |

Routes à confirmer explicitement :

- `POST /users/login` : corps, réponse, codes d’erreur ;
- `GET /users/me` : jeton requis, réponse ;
- `POST /users/register` : corps, réponse, codes d’erreur ;
- `GET /.well-known/jwks.json` : réponse et accessibilité depuis Swarm ;
- toute route supplémentaire utilisée par le Gateway ou le coach.

### Fiche Financial Coach

| Élément | Réponse |
|---|---|
| Responsable / équipe | |
| Dépôt et version Git | |
| Nom complet de l’image | |
| Tag immuable ou digest | |
| Image construite et poussée ? | Oui / Non — par : — date/version : |
| Tous les nœuds Swarm peuvent tirer l’image ? | Oui / Non — précisions : |
| Registre privé ? | Oui / Non |
| Authentification au registre | |
| Port interne | `8082` confirmé par le Dockerfile |
| Écoute sur `0.0.0.0` | Oui / Non — preuve : |
| Health/liveness | |
| Readiness | |
| Variables d’environnement | |
| Ressources CPU/mémoire | |
| Politique de redémarrage | |

La valeur exacte de chaque variable doit être confirmée :

| Variable | Rôle |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Doit valoir `prod` en production |
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL atteignable depuis Swarm |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur PostgreSQL dédié |
| `JWKS_URI` | URL interne du JWKS Identity |
| `JWT_ISSUER` | Valeur exacte du claim `iss` |
| `JWT_AUDIENCE` | Audience exacte attendue par Financial Coach |
| `SERVICES_TRANSACTION_SERVICE_BASE_URL` | URL interne du Transaction Service |
| `SCA_PROVIDER_BASE_URL` | URL du fournisseur SCA |
| `AI_BASE_URL` | URL compatible OpenAI |
| `AI_MODEL` | Modèle réellement autorisé |

### Fiche PostgreSQL

| Élément | Réponse |
|---|---|
| Instance | Docker externe / service externe / autre |
| Image/version si conteneur | |
| Port interne | `5432` pour PostgreSQL |
| Nom DNS ou hôte | |
| Réseau Swarm | |
| Base / utilisateur dédié | |
| Secret du mot de passe | |
| Flyway exécuté par | |
| Volume/persistance | |
| Sauvegarde | |
| Restauration testée | |
| Stratégie de migration | |
| Plan de retour arrière | |

Le fichier `docker-compose.yml` fournit PostgreSQL uniquement pour le développement local. Le fichier `docker-stack.yml` attend une base PostgreSQL externe. Ne déployez pas une base de test sur une donnée de production.

### Fiche des autres dépendances

Pour chaque service, remplir une ligne distincte :

| Dépendance | Image/version | Port interne | Réseau | Nom DNS | Secret requis | Santé |
|---|---:|---:|---|---|---|---|
| Identity Service | | | | | | |
| PostgreSQL | | `5432` | | | Mot de passe | |
| Transaction Service | | | | | Éventuellement | |
| Fournisseur SCA | | | | | Token API | |
| Fournisseur IA | URL externe | HTTPS | Sortie réseau | | Clé API | |

Ne déduisez pas que l’application démarrée est prête. Vérifiez depuis un conteneur du réseau Swarm que chaque dépendance est joignable et authentifie correctement ses requêtes.

## 2. Valider le contrat JWT

Obtenir la confirmation écrite des équipes Identity et Gateway.

- [ ] Identity signe les JWT avec RS256.
- [ ] Identity fournit la valeur exacte du claim `iss`.
- [ ] Financial Coach reçoit exactement cette valeur dans `JWT_ISSUER`.
- [ ] La JWKS est accessible depuis le réseau Swarm.
- [ ] L’URL Production JWKS est celle du service interne, pas `localhost`.
- [ ] Chaque JWT contient un `sub` UUID.
- [ ] Chaque JWT contient `role`.
- [ ] Identity confirme les valeurs exactes de `role`.
- [ ] Gateway confirme si il attend `USER`, `ROLE_USER`, `ADMIN` ou `ROLE_ADMIN`.
- [ ] Identity fournit la valeur exacte de `aud`.
- [ ] Identity confirme l’expiration et les éventuels autres claims.
- [ ] La rotation des clés n’interrompt pas l’accès au JWKS.
- [ ] La clé privée RSA reste chez Identity uniquement.
- [ ] Gateway ne reçoit que les clés publiques.

Exemple attendu côté coach :

```text
iss = valeur exacte émise
aud = valeur audience dédiée au coach
sub = UUID
role = ROLE_USER pour les routes métier
role = ROLE_ADMIN pour les routes Actuator
```

Ne créez pas vous-même de JWT ni ne signez une clé privée pour contourner un échec de contrat.

## 3. Préparer les images

### 3.1 Financial Coach

Depuis la racine du dépôt Financial Coach :

```powershell
git status
git rev-parse --short HEAD
.\mvnw.cmd clean verify
docker build -t <REGISTRY>/<ORGANISATION>/financial-coach:<VERSION> .
docker push <REGISTRY>/<ORGANISATION>/financial-coach:<VERSION>
docker inspect --format='{{index .RepoDigests 0}}' <REGISTRY>/<ORGANISATION>/financial-coach:<VERSION>
```

Remplacez les valeurs entre `<...>` sans les mettre dans Git. Exemple de forme :

```text
registry.example.com/bank/financial-coach:2026.11.25-1
registry.example.com/bank/financial-coach@sha256:...
```

Utilisez de préférence le digest immuable pour le déploiement. Testez aussi l’exécution avec l’utilisateur non-root fourni par le Dockerfile. Les secrets ne doivent pas être intégrés dans les couches d’image.

### 3.2 Identity

Dans le dépôt Identity, demander à l’équipe Identity de fournir :

```text
Adresse exacte du registre :
Tag immuable ou digest :
Commit Git versionné :
Résultat de vérification du push :
Architecture des images : amd64/arm64
```

Demandez à l’équipe de construire et de pousser la même image testée sur tous les nœuds ciblés.

### 3.3 Tester les plateformes

Sur chaque gestionnaire et chaque nœud capable d’exécuter les services :

```powershell
docker run --rm <IMAGE> echo ok
docker info --format '{{.Architecture}}'
```

Si le registre est privé, continuez avec la section suivante. Toute erreur de plateforme, d’architecture ou d’authentification doit être résolue avant le déploiement.

## 4. Gérer le registre privé

N’inscrivez jamais le nom d’utilisateur, le mot de passe ou le token dans `docker-stack.yml`, `.env`, un README ou Git.

### 4.1 Connexion du gestionnaire

Le gestionnaire qui lance `docker stack deploy` doit pouvoir se connecter au registre :

```powershell
docker login <REGISTRY>
```

En automatisation, lire le mot de passe depuis un gestionnaire de secrets ou un agent sécurisé et transmettre sa sortie sur l’entrée standard. Ne stockez pas la valeur dans l’historique PowerShell si possible.

Lors du déploiement, transmettez les informations d’authentification au contrôle Swarm :

```powershell
docker stack deploy --with-registry-auth --compose-file docker-stack.yml <STACK>
```

Cette option utilise les informations du gestionnaire pour autoriser les nœuds à tirer l’image. Elle ne les écrit pas dans le fichier YAML.

### 4.2 Renouvellement

Lorsqu’un token expire :

1. Générer ou renouveler les identifiants via le processus approuvé du registre.
2. Vérifier avec `docker login`.
3. Relancer les déploiements nécessaires avec `docker stack deploy --with-registry-auth`.
4. Supprimer les anciennes sessions/jetons selon la procédure du registre.

Pour une CI, utilisez un compte de déploiement dédié, des autorisations de lecture limitée et un secret fourni par le moteur CI. Révoquez immédiatement tout secret exposé.

Si Swarm utilise des identifiants préinstallés sur chaque nœud, utilisez `docker login` sur chaque nœud approuvé et documentez un calendrier de rotation. La stack ne doit pas contenir ces identifiants.

## 5. Préparer les réseaux

Créer ou vérifier le réseau externe utilisé par la stack :

```powershell
docker network ls
docker network inspect platform-backend
```

Pour créer le réseau lorsqu’il n’existe pas :

```powershell
docker network create --driver overlay --attachable platform-backend
```

Tous les services qui doivent communiquer doivent partager le même réseau. Ne publiez pas inutilement leurs ports sur Internet. Identity, Financial Coach, PostgreSQL, Transaction Service et SCA ne doivent exposer publiquement que les ports et routes approuvés.

## 6. Préparer les secrets Swarm

### 6.1 Secret runtime de Financial Coach

La stack attend le secret externe :

```text
financial_coach_runtime_config
```

Ce secret est fourni à `/run/secrets/financial-coach-secrets.yaml` et doit être un fichier YAML Spring. Il peut contenir :

```yaml
spring:
  datasource:
    password: "<mot-de-passe-stocké-en-secret>"
  ai:
    openai:
      api-key: "<clé-stockée-en-secret>"
services:
  sca:
    api-token: "<jeton-stocké-en-secret>"
```

Le YAML réel doit être composé en local à partir des valeurs fournies par un gestionnaire de secrets approuvé. Ne le placez pas dans Git.

Pour créer le secret, ne conservez pas le fichier temporaire :

```powershell
docker secret ls
docker secret create financial_coach_runtime_config .\financial-coach-secrets.yaml
Remove-Item .\financial-coach-secrets.yaml
```

Si il existe déjà, ne l’écrasez pas sans une procédure de rotation :

```powershell
docker secret inspect financial_coach_runtime_config
```

Pour une mise à jour de secret Swarm, créez une nouvelle ressource nommée avec une version, comme `financial_coach_runtime_config_v2`, adaptez la stack puis redéployez. La suppression ne doit être faite qu’après retour réussi de tous les services.

### 6.2 Secrets Identity

Chaque équipe Identity doit documenter quels secrets Swarm ou External Secrets leurs tâches lisent :

- mot de passe PostgreSQL ;
- clé privée RS256 ;
- mot de passe/secret du broker éventuel ;
- autres certificats ou jetons ;
- procédure de rotation.

Ne distribuez jamais la clé privée RS256 à Gateway ou Financial Coach.

## 7. Préparer les variables de déploiement

Définissez les variables uniquement dans le terminal de déploiement approuvé :

```powershell
$env:FINANCIAL_COACH_IMAGE="<IMAGE_OU_DIGEST>"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://<HOST_DB>:5432/<BASE>"
$env:SPRING_DATASOURCE_USERNAME="<UTILISATEUR_DEDIÉ>"
$env:JWKS_URI="http://<IDENTITY_DNS>:<PORT>/.well-known/jwks.json"
$env:JWT_ISSUER="<ISS_EXACT>"
$env:JWT_AUDIENCE="<AUD_EXACT>"
$env:SERVICES_TRANSACTION_SERVICE_BASE_URL="http://<TRANSACTION_DNS>:<PORT>"
$env:SCA_PROVIDER_BASE_URL="https://<SCA_DNS>"
$env:AI_BASE_URL="https://api.groq.com/openai"
$env:AI_MODEL="<MODELE_APPROUVE>"
```

Ne mettez pas les valeurs réelles dans `.env` versionné. Pour les informations secrètes, utilisez Swarm secret ou le gestionnaire de secrets approuvé.

Vérifiez la configuration sans afficher les secrets :

```powershell
docker stack config --compose-file docker-stack.yml
docker stack config --compose-file docker-stack.yml | Select-String "source:|image:|target:"
```

## 8. Vérifier PostgreSQL et les migrations

Avant le déploiement :

1. Créer une base dédiée et un utilisateur avec les permissions minimales.
2. Interdire à l’application le rôle superutilisateur `postgres`.
3. Vérifier la résolution DNS et le port depuis le réseau `platform-backend`.
4. Vérifier TLS ou la politique réseau exigée par l’organisation.
5. Effectuer une sauvegarde immédiate avant la migration.
6. Confirmer que Flyway dispose des droits nécessaires.
7. Confirmer les migrations V1 à V7 du README20.
8. Préparer le retour arrière si une migration échoue.
9. Faire un déploiement progressif uniquement si Flyway reste compatible avec les anciennes tâches.

Test depuis une tâche temporaire autorisée :

```powershell
docker run --rm --network platform-backend postgres:17-alpine `
  pg_isready -h <HOST_DB> -p 5432 -U <UTILISATEUR_DEDIÉ>
```

Ne publiez pas le mot de passe dans la commande. Utilisez une session ou un mécanisme approuvé pour fournir les identifiants.

## 9. Déployer dans l’ordre

1. Sauvegarder PostgreSQL.
2. Déployer ou vérifier Identity.
3. Vérifier `/.well-known/jwks.json` depuis le réseau Swarm.
4. Déployer ou vérifier les dépendances requises : Transaction Service et SCA.
5. Déployer Financial Coach.
6. Attendre les tâches démarrées.
7. Vérifier les migrations Flyway.
8. Vérifier les journaux.
9. Vérifier la santé.
10. Vérifier le login JWT et une requête métier.

Déployer la stack :

```powershell
docker stack deploy --with-registry-auth --compose-file docker-stack.yml <STACK>
docker stack services <STACK>
docker stack ps <STACK> --no-trunc
docker service ls
docker service inspect <STACK>_financial-coach-service --pretty
```

Le service doit utiliser la stack nommée réellement créée. Vérifiez les noms avec `docker stack ls`.

## 10. Vérifier après déploiement

### Santé

Depuis un nœud ou un conteneur autorisé :

```powershell
Invoke-RestMethod -Method Get -Uri "http://<DNS_FINANCIAL_COACH>:8082/actuator/health"
```

Cette route peut exiger `ROLE_ADMIN` selon la configuration Spring Security. Ne publiez pas l’Actuator sur Internet. Les chemins exacts de liveness et readiness Swarm doivent être fournis et confirmés par l’équipe.

### JWKS

Depuis le réseau Swarm :

```powershell
Invoke-RestMethod -Method Get -Uri "http://<IDENTITY_DNS>:<PORT>/.well-known/jwks.json"
```

Vérifiez que les clés sont accessibles mais ne publiez pas les clés privées.

### JWT

Avec un compte fictif `ROLE_USER`, vérifier les points suivants :

- login réussi ;
- `sub` est un UUID ;
- `iss` correspond exactement à `JWT_ISSUER` ;
- `aud` correspond exactement à `JWT_AUDIENCE` ;
- `role` permet `ROLE_USER` ;
- une route `/api/v1/coach/**` répond correctement ;
- une route Actuator répond seulement avec un rôle administrateur.

Ne placez aucun JWT réel dans les captures, les journaux ou les tickets.

### Journaux

```powershell
docker service logs --timestamps --tail 200 <STACK>_financial-coach-service
docker service logs --timestamps --tail 200 <STACK>_<SERVICE_IDENTITY>
```

Rechercher :

- échec de connexion PostgreSQL ;
- Flyway bloqué ;
- JWKS inaccessible ;
- issuer/audience/audience invalide ;
- absence de secret ;
- erreurs SCA ;
- erreur de clé API IA ;
- échec de résolution DNS ;
- tâches redémarrées en boucle.

Ne recopiez pas les valeurs de token ou de mot de passe trouvées dans les journaux.

## 11. Vérifier une mise à jour

Avant toute mise à jour :

- [ ] Image publiée avec digest ou tag immuable ;
- [ ] Sauvegarde PostgreSQL réussie ;
- [ ] Migrations compatibles avec les anciennes tâches ;
- [ ] Secrets et JWKS disponibles ;
- [ ] Capacité des nœuds suffisante ;
- [ ] Healthcheck/liveness/readiness confirmé ;
- [ ] Plan de rollback prêt ;
- [ ] Fenêtre de maintenance approuvée.

Déployer une nouvelle image en mettant à jour uniquement sa référence :

```powershell
$env:FINANCIAL_COACH_IMAGE="<NOUVELLE_IMAGE_OU_DIGEST>"
docker stack deploy --with-registry-auth --compose-file docker-stack.yml <STACK>
docker stack ps <STACK> --no-trunc
docker service rollback <STACK>_financial-coach-service
```

Évitez un rollback SGBD automatique. Une migration destructive ou incompatible peut nécessiter une restauration validée.

## 12. Checklist de fin

- [ ] Les fiches Identity et Financial sont complètes.
- [ ] Toutes les dépendances ont un propriétaire.
- [ ] Les images sont immuables et vérifiées.
- [ ] Les nœuds Swarm peuvent tirer les images.
- [ ] Le mécanisme de registre privé est testé et renouvelable.
- [ ] Aucun secret n’est présent dans Git ou les fichiers YAML.
- [ ] Les secrets Swarm et leur rotation sont documentés.
- [ ] Identity, PostgreSQL, Transaction Service, SCA et IA sont accessibles.
- [ ] JWKS est accessible depuis le réseau Swarm.
- [ ] `iss`, `aud`, `sub` et `role` sont validés.
- [ ] PostgreSQL est sauvegardé.
- [ ] Flyway est compatible avec la mise à jour progressive.
- [ ] Les ressources CPU/mémoire sont définies.
- [ ] Les checks liveness/readiness sont confirmés.
- [ ] Les journaux ne contiennent pas de secrets.
- [ ] La procédure de rollback est testée.
- [ ] Les membres des deux équipes ont signé les fiches de pré-déploiement.
