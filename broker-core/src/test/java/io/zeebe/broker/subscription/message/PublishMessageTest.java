/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.subscription.message;

import static io.zeebe.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.intent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.test.MsgPackUtil;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandRequestBuilder;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class PublishMessageTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Test
  public void shouldPublishMessage() {

    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .done()
            .sendAndAwait();

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue())
        .containsExactly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("payload", EMTPY_OBJECT),
            entry("messageId", ""));

    final SubscribedRecord publishedEvent =
        apiRule
            .topic()
            .receiveEvents()
            .filter(intent(MessageIntent.PUBLISHED))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no follow-up event found"));

    assertThat(publishedEvent.value())
        .containsExactly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("payload", EMTPY_OBJECT),
            entry("messageId", ""));
  }

  @Test
  public void shouldPublishMessageWithPayload() {

    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("payload", MsgPackUtil.MSGPACK_PAYLOAD)
            .done()
            .sendAndAwait();

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue()).contains(entry("payload", MsgPackUtil.MSGPACK_PAYLOAD));
  }

  @Test
  public void shouldPublishMessageWithMessageId() {

    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("messageId", "msg-1")
            .done()
            .sendAndAwait();

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue()).contains(entry("messageId", "msg-1"));
  }

  @Test
  public void shouldPublishSecondMessageWithDifferenId() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-123", "msg-2");

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentName() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order shipped", "order-123", "msg-1");

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSecondMessageWithDiffentCorrelationKey() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-456", "msg-1");

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSameMessageWithEmptyId() {

    publishMessage("order canceled", "order-123", "");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-123", "");

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSameMessageWithoutId() {

    apiRule
        .createCmdRequest()
        .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
        .command()
        .put("name", "order canceled")
        .put("correlationKey", "order-123")
        .done()
        .sendAndAwait();

    final ExecuteCommandResponse response =
        apiRule
            .createCmdRequest()
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .done()
            .sendAndAwait();

    assertThat(response.intent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldRejectToPublishSameMessageWithId() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-123", "msg-1");

    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(response.rejectionReason())
        .isEqualTo("message with id 'msg-1' is already published");

    final SubscribedRecord rejection =
        apiRule
            .topic()
            .receiveRejections()
            .filter(intent(MessageIntent.PUBLISH))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no rejection found"));

    assertThat(rejection.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(rejection.rejectionReason())
        .isEqualTo("message with id 'msg-1' is already published");
  }

  @Test
  public void shouldFailToPublishMessageWithoutName() {

    final ExecuteCommandRequestBuilder request =
        apiRule
            .createCmdRequest()
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("correlationKey", "order-123")
            .done();

    assertThatThrownBy(() -> request.sendAndAwait())
        .hasMessageContaining("Property 'name' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutCorrelationKey() {

    final ExecuteCommandRequestBuilder request =
        apiRule
            .createCmdRequest()
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("name", "order canceled")
            .done();

    assertThatThrownBy(() -> request.sendAndAwait())
        .hasMessageContaining("Property 'correlationKey' has no valid value");
  }

  private ExecuteCommandResponse publishMessage(
      String name, String correlationKey, String messageId) {

    return apiRule
        .createCmdRequest()
        .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
        .command()
        .put("name", name)
        .put("correlationKey", correlationKey)
        .put("messageId", messageId)
        .done()
        .sendAndAwait();
  }
}
