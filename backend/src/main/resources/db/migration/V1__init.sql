-- V1: 技术栈预置清单 + 官方文档映射
CREATE TABLE IF NOT EXISTS tech_stacks (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL UNIQUE,
    aliases             VARCHAR(500),
    official_doc_url    VARCHAR(500),
    doc_key_pages       JSONB,
    github_search_query VARCHAR(200),
    description         VARCHAR(1000),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO tech_stacks (name, aliases, official_doc_url, doc_key_pages, github_search_query, description) VALUES
('React', 'reactjs,react.js', 'https://react.dev', '["https://react.dev/learn", "https://react.dev/reference/react"]', 'topic:react', '用于构建用户界面的 JavaScript 库'),
('Vue', 'vuejs,vue.js', 'https://vuejs.org', '["https://vuejs.org/guide/introduction", "https://vuejs.org/guide/essentials/application"]', 'topic:vue', '渐进式 JavaScript 框架'),
('Angular', 'angular.js', 'https://angular.dev', '["https://angular.dev/overview", "https://angular.dev/tutorials"]', 'topic:angular', '基于 TypeScript 的前端框架'),
('Svelte', 'sveltejs', 'https://svelte.dev', '["https://svelte.dev/docs/svelte/overview", "https://svelte.dev/tutorial/svelte/welcome-to-svelte"]', 'topic:svelte', '编译型前端框架'),
('Spring Boot', 'springboot,spring-boot', 'https://spring.io/projects/spring-boot', '["https://spring.io/guides/gs/spring-boot", "https://docs.spring.io/spring-boot/index.html"]', 'topic:spring-boot', 'Java 企业级应用开发框架'),
('Django', '', 'https://www.djangoproject.com', '["https://docs.djangoproject.com/en/stable/intro/overview/", "https://docs.djangoproject.com/en/stable/intro/tutorial01/"]', 'topic:django', 'Python 全栈 Web 框架'),
('Flask', '', 'https://flask.palletsprojects.com', '["https://flask.palletsprojects.com/en/stable/quickstart/", "https://flask.palletsprojects.com/en/stable/tutorial/"]', 'topic:flask', 'Python 轻量级 Web 框架'),
('Express', 'express.js,expressjs', 'https://expressjs.com', '["https://expressjs.com/en/starter/installing.html", "https://expressjs.com/en/guide/routing.html"]', 'topic:express', 'Node.js Web 应用框架'),
('NestJS', 'nestjs,nest.js', 'https://nestjs.com', '["https://docs.nestjs.com/first-steps", "https://docs.nestjs.com/controllers"]', 'topic:nestjs', 'Node.js 企业级框架（TypeScript）'),
('Gin', 'gin-gonic', 'https://gin-gonic.com', '["https://gin-gonic.com/docs/quickstart/", "https://gin-gonic.com/docs/examples/"]', 'topic:gin', 'Go 高性能 Web 框架'),
('Flutter', '', 'https://flutter.dev', '["https://docs.flutter.dev/get-started/install", "https://docs.flutter.dev/get-started/codelab"]', 'topic:flutter', '跨平台 UI 框架（Dart）'),
('React Native', 'reactnative,react-native', 'https://reactnative.dev', '["https://reactnative.dev/docs/getting-started", "https://reactnative.dev/docs/intro-react-native-components"]', 'topic:react-native', '跨平台移动应用框架'),
('PyTorch', '', 'https://pytorch.org', '["https://pytorch.org/get-started/locally/", "https://pytorch.org/tutorials/beginner/basics/intro.html"]', 'topic:pytorch', '深度学习框架'),
('TensorFlow', '', 'https://www.tensorflow.org', '["https://www.tensorflow.org/install", "https://www.tensorflow.org/tutorials/quickstart/beginner"]', 'topic:tensorflow', '机器学习框架'),
('LangChain', '', 'https://python.langchain.com', '["https://python.langchain.com/docs/introduction/", "https://python.langchain.com/docs/tutorials/"]', 'topic:langchain', '大模型应用开发框架'),
('Pandas', '', 'https://pandas.pydata.org', '["https://pandas.pydata.org/docs/getting_started/index.html", "https://pandas.pydata.org/docs/user_guide/10min.html"]', 'topic:pandas', 'Python 数据分析库'),
('Redis', '', 'https://redis.io', '["https://redis.io/docs/latest/get-started/", "https://redis.io/docs/latest/develop/data-types/"]', 'topic:redis', '内存数据结构存储'),
('Kafka', 'apache-kafka', 'https://kafka.apache.org', '["https://kafka.apache.org/documentation/", "https://kafka.apache.org/quickstart"]', 'topic:apache-kafka', '分布式事件流平台'),
('Docker', '', 'https://docs.docker.com', '["https://docs.docker.com/get-started/", "https://docs.docker.com/guides/"]', 'topic:docker', '容器化平台'),
('Kubernetes', 'k8s', 'https://kubernetes.io', '["https://kubernetes.io/docs/tutorials/kubernetes-basics/", "https://kubernetes.io/docs/concepts/overview/"]', 'topic:kubernetes', '容器编排平台'),
('Nginx', '', 'https://nginx.org', '["https://nginx.org/en/docs/beginners_guide.html", "https://nginx.org/en/docs/http/ngx_http_core_module.html"]', 'topic:nginx', '高性能 Web 服务器与反向代理');
