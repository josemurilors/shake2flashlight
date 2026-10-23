# Plano da versão 1.3

**Objetivo:** permitir instalar o Shake2Flashlight fora da Play Store sem bloqueio, e fechar as pendências de teste da 1.2.

**Origem:** no teste com um Galaxy A32 (out/2026), o gesto funcionou bem, mas a instalação do APK foi bloqueada pela proteção da Play Store. As releases 1.1 e 1.2 são assinadas com a chave de **depuração**, que o Play Protect vê como suspeita. Além disso, o Google está exigindo **verificação de desenvolvedor** para instalar apps fora da loja em celulares certificados no Brasil.

## 1. Descobrir a causa exata do bloqueio

- [ ] Reproduzir a instalação no A32 e anotar a mensagem exata.
  - Se falar em "Play Protect" ou "app bloqueado para proteger seu dispositivo", seguir as etapas 2 e 3.
  - Se falar em "desenvolvedor não verificado", seguir as etapas 2 e 4.
- [ ] Conferir na documentação oficial do Android como a verificação de desenvolvedor está valendo hoje no Brasil (prazos, conta para hobby, exceção para `adb install`).

**Contorno enquanto isso:** na janela do Play Protect, tocar em "Mais detalhes" e depois em "Instalar mesmo assim". Também dá para instalar pelo cabo com `adb install`.

## 2. Assinatura de release (base para todo o resto)

- [ ] Gerar uma chave de release com o `keytool` do JDK 21, guardada **fora do repositório**. Exemplo: `%USERPROFILE%\.android\shake2flashlight-release.jks`.
- [ ] Guardar as senhas e o caminho da chave em `~/.gradle/gradle.properties` (fora do git), com as propriedades `S2F_STORE_FILE`, `S2F_STORE_PASSWORD`, `S2F_KEY_ALIAS` e `S2F_KEY_PASSWORD`.
- [ ] Criar o `signingConfig` de release no `app/build.gradle.kts`, lendo essas propriedades. Sem elas, o build de release deve falhar com uma mensagem clara, em vez de cair na chave de depuração.
- [ ] Fazer backup da chave e das senhas em dois lugares seguros. **Perder a chave impede atualizar o app** para quem já o instalou.
- [ ] Gerar o APK com `./gradlew assembleRelease` e conferir a assinatura com `apksigner verify --print-certs`.
- [ ] Avaliar ativar o R8 (`isMinifyEnabled = true`) para reduzir o APK. Se ativar, testar o app inteiro de novo.

**Consequência:** quem tem a 1.1 ou a 1.2 instalada precisa **desinstalar antes** de instalar a 1.3, porque a chave muda. Isso deve ficar em destaque nas notas da release.

## 3. Play Protect

- [ ] Instalar o APK assinado com a chave de release no A32 e ver se ainda é bloqueado.
- [ ] Se for, enviar o app para análise pelo formulário de contestação do Play Protect, informando o nome do pacote `com.shake2flashlight` e a chave de release.
- [ ] Revisar o que costuma acender o alerta: o pedido de isenção de otimização de bateria (o lint já avisa que ele viola a política da Play Store) e o serviço em primeiro plano `specialUse`. Decidir se o pedido de isenção vira só uma orientação na tela, sem abrir o diálogo do sistema.

## 4. Verificação de desenvolvedor

- [ ] Criar a conta no Android Developer Console (há uma modalidade gratuita para quem desenvolve por hobby).
- [ ] Registrar o pacote `com.shake2flashlight` com o certificado da chave de release.
- [ ] Instalar no A32 a partir do APK baixado da release, sem cabo, e confirmar que não há bloqueio.

## 5. Pendências de teste herdadas da 1.2

- [ ] Tema claro com as cores do papel de parede (Android 12+) num aparelho físico.
- [ ] "Iniciar ao ligar o celular": reiniciar o aparelho e conferir.
- [ ] "Desligar sozinha": esperar 1 minuto e ver a lanterna apagar.
- [ ] Botão do painel rápido: adicionar e usar.
- [ ] "Ignorar no bolso": testar com o celular no bolso.
- [ ] Disparos sem querer: um dia de uso normal no S23 Ultra e no A32, contando os acionamentos indevidos.
- [ ] Consumo de bateria após algumas horas com o gesto ativo.

## 6. Entrega

- [ ] Subir `versionCode` para 4 e `versionName` para "1.3".
- [ ] Atualizar o README: a forma de instalar, o aviso de desinstalar a versão antiga e a lista de aparelhos testados.
- [ ] Publicar a release v1.3 com o APK assinado e notas explicando a troca de chave.

## Fora desta versão

- Publicar na Play Store.
- Calibração guiada no app (gravar o gesto do usuário para ajustar os limites).
- Detecção pelo sensor hub para gastar menos bateria.
