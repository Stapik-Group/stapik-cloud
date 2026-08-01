package pl.stapik.cloud.audit;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.audit.dto.AuditLogEntryInfo;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditingAspect {
    private final AuditLogService auditLogService;

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @AfterReturning(pointcut = "@annotation(auditing)", returning = "result")
    public void audit(JoinPoint joinPoint, Auditing auditing, Object result) {
        StandardEvaluationContext context = buildContext(joinPoint, result);

        UUID extensionId = evaluate(auditing.extensionId(), context, UUID.class);
        String actor = auditing.actor().isBlank() ? currentActor() : evaluate(auditing.actor(), context, String.class);
        String details = auditing.details().isBlank() ? null : evaluate(auditing.details(), context, String.class);

        auditLogService.saveEntry(AuditLogEntryInfo.of(extensionId, actor, auditing.action(), details));
    }

    private StandardEvaluationContext buildContext(JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        context.setVariable("result", result);
        return context;
    }

    private <T> T evaluate(String expression, StandardEvaluationContext context, Class<T> type) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return expressionParser.parseExpression(expression).getValue(context, type);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
