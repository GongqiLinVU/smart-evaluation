import { Row, Col, Statistic, Card } from 'antd';
import {
  FileTextOutlined,
  OrderedListOutlined,
  CodeOutlined,
  TableOutlined,
  PictureOutlined,
  LinkOutlined,
  FontSizeOutlined,
} from '@ant-design/icons';
import type { DocumentStatsResponse } from '../types';

interface DocumentStatsProps {
  stats: DocumentStatsResponse;
}

export default function DocumentStats({ stats }: DocumentStatsProps) {
  const items = [
    { title: 'Total Words', value: stats.totalWordCount, icon: <FontSizeOutlined /> },
    { title: 'Headings', value: stats.headingCount, icon: <OrderedListOutlined /> },
    { title: 'Sections', value: stats.sectionCount, icon: <FileTextOutlined /> },
    { title: 'Code Snippets', value: stats.codeSnippetCount, icon: <CodeOutlined /> },
    { title: 'Tables', value: stats.tableCount, icon: <TableOutlined /> },
    { title: 'Images', value: stats.imageCount, icon: <PictureOutlined /> },
    { title: 'Links', value: stats.linkCount, icon: <LinkOutlined /> },
  ];

  return (
    <Card title="Document Statistics" style={{ marginBottom: 24 }}>
      <Row gutter={[16, 16]}>
        {items.map((item) => (
          <Col key={item.title} xs={12} sm={8} md={6} lg={3}>
            <Statistic
              title={item.title}
              value={item.value}
              prefix={item.icon}
            />
          </Col>
        ))}
      </Row>
    </Card>
  );
}
