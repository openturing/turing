# Integração Keycloak — Viglet Turing ES

Este guia descreve como configurar o **Keycloak** para que o Viglet Turing
ES consiga autenticar usuários via OIDC **e** consumir a Admin REST API do
Keycloak para popular as páginas de **Administration → Users** e
**Administration → Groups** com os usuários e grupos do realm.

> Quando `turing.keycloak=true`, as páginas de Users/Groups deixam de ler
> as tabelas locais (`auth_user`/`auth_group`) e passam a exibir os
> usuários/grupos do realm Keycloak (somente leitura). As associações
> entre grupos e usuários também são exibidas.

---

## 1. Pré-requisitos

- Keycloak **22+** em execução (ex.: `http://localhost:8080`)
- Acesso administrativo ao Keycloak (`master` realm) para criar realm,
  client e papéis
- Viglet Turing ES rodando localmente em `http://localhost:2700`

Para subir o Keycloak rapidamente em modo dev:

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:24.0 start-dev
```

---

## 2. Criar o realm

1. Abra o Keycloak Admin Console: `http://localhost:8080/admin`.
2. No menu de realms (canto superior esquerdo), clique em **Create realm**.
3. Defina o **Name** como `demo` (ou outro de sua preferência) e clique
   em **Create**.

Anote o issuer:

```
http://localhost:8080/realms/demo
```

---

## 3. Criar o client OIDC para o Turing

1. Em **Clients → Create client**, configure:

   | Campo                                  | Valor                                      |
   |----------------------------------------|--------------------------------------------|
   | Client type                            | OpenID Connect                             |
   | Client ID                              | `turing`                                   |
   | Name                                   | Viglet Turing ES                           |
   | Always display in console              | On (opcional)                              |

2. Em **Capability config**:

   | Campo                          | Valor          |
   |--------------------------------|----------------|
   | Client authentication          | **On**         |
   | Authorization                  | Off            |
   | Standard flow                  | **On**         |
   | Direct access grants           | On             |
   | Service accounts roles         | **On** (opcional, ver §5.b) |

3. Em **Login settings**:

   | Campo                       | Valor                                                            |
   |-----------------------------|------------------------------------------------------------------|
   | Root URL                    | `http://localhost:2700`                                          |
   | Home URL                    | `http://localhost:2700`                                          |
   | Valid redirect URIs         | `http://localhost:2700/login/oauth2/code/keycloak`               |
   | Valid post logout redirect URIs | `http://localhost:2700`                                      |
   | Web origins                 | `+` (libera os origens dos redirect URIs)                        |

4. Salve. Vá até a aba **Credentials** e copie o **Client secret** —
   ele será usado em `application.yaml` mais adiante.

---

## 4. Conferir o escopo `openid`

O Turing usa **OIDC** (`scope: openid`). Garanta que o client
`turing` tenha o escopo padrão `openid`:

1. Abra **Clients → turing → Client scopes**.
2. Confirme que `openid` está em **Default** (já vem por padrão).
3. Os escopos `profile` e `email` também precisam estar em **Default**
   (já vêm por padrão) — eles preenchem `preferred_username`,
   `given_name`, `family_name` e `email` no token, usados pelo
   `TurOidcUserService` para popular o perfil local.

---

## 5. Conceder permissões para a Admin REST API

A nova `TurKeycloakAdminService` chama a Admin REST API do Keycloak
(`/admin/realms/{realm}/users`, `/groups`, etc.) usando o **access
token do usuário logado**. Por isso, o usuário precisa ter as roles
`realm-management` adequadas no Keycloak.

> Sem essas roles, o navegador exibirá `403 Forbidden` ao abrir
> Administration → Users/Groups quando `turing.keycloak=true`.

### 5.a Conceder ao usuário administrador do Turing

Para o usuário que acessará o painel Administration:

1. Vá em **Users → (selecione o usuário) → Role mapping**.
2. Clique em **Assign role** e marque **Filter by clients** →
   `realm-management`.
3. Atribua, no mínimo:
   - `view-users`
   - `query-users`
   - `query-groups`
   - `view-realm`
4. Para conceder acesso completo, atribua `realm-admin`.

> O ID desse usuário (`username`) deve coincidir com o valor
> configurado em `turing.keycloak-admin-id` (ver §6) — assim o Turing
> garante que ele entre no grupo local `Administrator` no primeiro
> login.

### 5.b (Opcional) Service account em vez de token do usuário

Se preferir não depender das permissões individuais do usuário, é
possível habilitar **Service accounts roles** no client `turing`
(passo §3) e dar a ele as mesmas roles `realm-management` listadas
acima. Esta variação exige um pequeno ajuste no
`TurKeycloakAdminService` (trocar o fluxo `OAuth2AuthorizedClient` por
`client_credentials`) — não vem habilitado por padrão.

---

## 6. Configurar o `application.yaml` do Turing

Edite `turing-app/src/main/resources/application.yaml` (ou o seu
override por ambiente) com os valores anotados nos passos anteriores:

```yaml
turing:
  keycloak: true
  keycloak-admin-id: admin              # username do admin no Keycloak
  url: http://localhost:2700

spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: turing
            client-secret: <CLIENT_SECRET_DA_ETAPA_3>
            scope: openid
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          keycloak:
            issuer-uri: http://localhost:8080/realms/demo
```

Pontos importantes:

- `turing.keycloak=true` ativa o fluxo Keycloak no
  `TurSecurityConfigProduction` e habilita as APIs
  `/api/v2/keycloak/users` e `/api/v2/keycloak/groups`.
- `turing.keycloak-admin-id` deve ser o `username` (campo
  `preferred_username` do token) do usuário com permissões de admin.
  No primeiro login, o `TurKeycloakAdminOnStartup` garante que esse
  usuário entre no grupo local `Administrator`.
- `issuer-uri` é o caminho `/realms/<nome>` do realm criado no §2.
  A `TurKeycloakAdminService` extrai automaticamente
  `http://host:8080/admin/realms/<nome>` a partir desse valor.
- `client-secret` deve ser carregado por variável de ambiente em
  produção (ex.: `${KEYCLOAK_CLIENT_SECRET}`).

---

## 7. Validar a integração

1. (Re)inicie o Turing.
2. Abra `http://localhost:2700/login` — você será redirecionado ao
   Keycloak. Faça login com o usuário do passo §5.a.
3. Acesse **Administration → Users**:
   - O cabeçalho mostrará a descrição
     *"Read-only directory of users sourced from the configured Keycloak realm."*
   - A lista deve refletir os usuários do realm (e não os da tabela
     local).
4. Abra um usuário — a tela exibe os atributos do Keycloak e a seção
   **Group memberships** com todos os grupos do realm aos quais o
   usuário pertence.
5. Acesse **Administration → Groups** e abra um grupo — a seção
   **Members** lista os usuários reais do Keycloak associados.

### Endpoints úteis para debug

| Endpoint                                | O que retorna                                |
|-----------------------------------------|----------------------------------------------|
| `GET /api/discovery`                    | Deve trazer `"keycloak": true`               |
| `GET /api/v2/keycloak/users`            | Lista de usuários do realm (requer login)    |
| `GET /api/v2/keycloak/users/{username}` | Detalhe + grupos do usuário                  |
| `GET /api/v2/keycloak/groups`           | Lista de grupos do realm                     |
| `GET /api/v2/keycloak/groups/{id}`      | Detalhe + membros do grupo                   |

Todos exigem autenticação como `ROLE_ADMIN` no Turing **e** as roles
`realm-management` correspondentes no Keycloak.

---

## 8. Solução de problemas

| Sintoma                                                              | Causa provável / correção                                                                                       |
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Página de Users/Groups continua mostrando dados locais               | `turing.keycloak` ainda está `false` ou `/api/discovery` não retorna `keycloak: true` — confira `application.yaml` e o restart. |
| `403 Forbidden` ao listar usuários/grupos                            | Falta de roles `realm-management` (`view-users`, `query-groups`, etc.) no usuário logado — ver §5.a.            |
| `409 Conflict` em `/api/v2/keycloak/users`                           | `turing.keycloak=false` ou `issuer-uri` vazio — habilite a flag e revise as propriedades.                       |
| `IllegalStateException: issuer-uri does not contain '/realms/<name>'`| O `issuer-uri` configurado não tem o segmento `/realms/<nome>` — use a URL do realm do Keycloak.                |
| Login Keycloak ok, mas o usuário não vira admin no Turing            | `turing.keycloak-admin-id` não bate com o `preferred_username` do usuário — ajuste o `application.yaml`.        |
| `redirect_uri_mismatch` ao logar                                     | O **Valid redirect URI** do client (`/login/oauth2/code/keycloak`) está diferente de `turing.url`.              |

---

## 9. Referências rápidas

- Especificação OIDC do Keycloak: <https://www.keycloak.org/docs/latest/server_admin/#_oidc>
- Admin REST API: <https://www.keycloak.org/docs-api/latest/rest-api/index.html>
- Roles `realm-management`: <https://www.keycloak.org/docs/latest/server_admin/#fine-grain-permissions>
- Spring Security OAuth2 Client: <https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html>
